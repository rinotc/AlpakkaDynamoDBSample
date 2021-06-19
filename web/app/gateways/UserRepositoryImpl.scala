package gateways

import akka.actor.ActorSystem
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import models.{Age, FullName, User, UserId, UserRepository}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, GetItemRequest}

import java.net.URI
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.{MapHasAsJava, MapHasAsScala}

class UserRepositoryImpl @Inject() (implicit ec: ExecutionContext, system: ActorSystem) extends UserRepository {

  implicit private val client: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder()
    .endpointOverride(URI.create("http://localhost:8000"))
    .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
    .build()

  system.registerOnTermination(client.close())

  override def find(id: UserId): Future[Option[User]] = {

    val keyToGet = Map("user_id" -> AttributeValue.builder().s(id.value.toString).build())

    val request = GetItemRequest
      .builder()
      .tableName("users")
      .key(keyToGet.asJava)
      .build()

    DynamoDb.single(request).map { response =>
      val resMap = response.item().asScala.toMap
      if (response.hasItem) {
        val userId = resMap("user_id").s()
        val name   = resMap("user_name").s()
        val age    = resMap("age").n().toInt

        val user = User(
          id = UserId.from(userId),
          name = FullName(name),
          age = Age(age)
        )
        Some(user)
      } else {
        None
      }
    }
  }
}