package example

import scala.compat.java8.FutureConverters
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }
import java.nio.charset.StandardCharsets
import collection.JavaConverters._
import play.api.libs.json.Json
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import com.amazonaws.services.lambda.runtime.events.{ APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse }
import com.amazonaws.services.translate.AmazonTranslateClient
import com.amazonaws.services.translate.model.TranslateTextRequest
import com.linecorp.bot.client.{ LineMessagingClient, LineSignatureValidator }
import com.linecorp.bot.model.ReplyMessage
import com.linecorp.bot.model.event.message.TextMessageContent
import com.linecorp.bot.model.event.{ CallbackRequest, MessageEvent }
import com.linecorp.bot.model.message.TextMessage
import com.linecorp.bot.model.objectmapper.ModelObjectMapper

object BotHandler extends RequestHandler[APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse] {

  /** AWS Lambda function handler from API Gateway */
  override def handleRequest(event: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse = {
    val logger   = context.getLogger
    val response = new APIGatewayV2HTTPResponse()
    response.setHeaders(Map("Content-Type" -> "application/json").asJava)

    // Validate line signature
    val valid = Option(event.getHeaders.get("x-line-signature")) match {
      case Some(signature) => {
        val channelSecret = "YOUR_CHANNEL_SECRET"
        new LineSignatureValidator(channelSecret.getBytes(StandardCharsets.UTF_8))
          .validateSignature(
            event.getBody.getBytes(StandardCharsets.UTF_8),
            signature
          )
      }
      case None            => false
    }
    if (!valid) {
      response.setStatusCode(401)
      response.setBody(Json.toJson(Map(
        "message" -> "Unauthorized : invalid line signature"
      )).toString)
      logger.log(response.toString)
      return response
    }

    // Read line call back request
    val lineCallBackReq = {
      ModelObjectMapper
        .createNewObjectMapper
        .readValue(
          event.getBody.getBytes(StandardCharsets.UTF_8),
          classOf[CallbackRequest]
        )
    }

    // Get request event and run line messaging api
    val (statusCode, message) = lineCallBackReq.getEvents.asScala.headOption.map {
      // Accept text message event only
      case eventOfText: MessageEvent[TextMessageContent] => {
        // Get received text message
        val receivedText = eventOfText.getMessage.getText

        // Create amazon translate client
        val translateClient = {
          AmazonTranslateClient.builder
            .withCredentials(new AWSStaticCredentialsProvider(
              DefaultAWSCredentialsProviderChain
                .getInstance
                .getCredentials
            ))
            .build
        }
        // Set translate request (translate from English to Japanese)
        val translateRequest = {
          new TranslateTextRequest()
            .withText(receivedText)
            .withSourceLanguageCode("en")
            .withTargetLanguageCode("ja")
        }
        // Run text translation
        val translatedText = {
          translateClient
            .translateText(translateRequest)
            .getTranslatedText
        }

        // Create line messaging client
        val lineClient = {
          val channelToken = "YOUR_CHANNEL_TOKEN"
          LineMessagingClient
            .builder(channelToken)
            .build
        }
        // Reply line message
        val lineResponse = FutureConverters.toScala {
          lineClient.replyMessage(new ReplyMessage(
            eventOfText.getReplyToken,
            new TextMessage(translatedText)
          ))
        }
        Await.ready(lineResponse, Duration.Inf)
        lineResponse.value.get match {
          case Success(_) => (200, s"Success : $receivedText >>> $translatedText")
          case Failure(e) => (500, s"Internal Server Error : ${ e.getMessage }")
        }
      }
      case _ => {
        (200, "Success : unexpected callback line event")
      }
    }.getOrElse {
      (200, "Success : no callback line event")
    }

    response.setStatusCode(statusCode)
    response.setBody(Json.toJson(Map(
      "message" -> message
    )).toString)
    logger.log(response.toString)
    response
  }
}
