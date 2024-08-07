package com.linagora.tmail.james.jmap.model

import java.time.ZonedDateTime
import java.util.UUID

import cats.data.Validated
import cats.instances.list._
import cats.syntax.traverse._
import com.linagora.tmail.james.jmap.method.{FirebaseSubscriptionSetUpdatePerformer, standardError}
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionPatchObject.updateProperties
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionUpdateFailure.LOGGER
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.ExpireTimeInvalidException.TIME_FORMATTER
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Properties.toProperties
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Id, Properties, SetError, UTCDate}
import org.apache.james.jmap.method.WithoutAccountId
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsArray, JsObject, JsPath, JsString, JsValue, JsonValidationError}

import scala.util.{Failure, Success, Try}

object FirebaseSubscriptionId {
  def generate(): FirebaseSubscriptionId = FirebaseSubscriptionId(UUID.randomUUID)

  def liftOrThrow(unparsedId: UnparsedFirebaseSubscriptionId): Either[IllegalArgumentException, FirebaseSubscriptionId] =
    liftOrThrow(unparsedId.id.value)

  def liftOrThrow(value: String): Either[IllegalArgumentException, FirebaseSubscriptionId] =
    Try(UUID.fromString(value))
      .map(value1 => FirebaseSubscriptionId(value1))
      .toEither
      .left.map(e => new IllegalArgumentException("FirebaseSubscriptionId is invalid", e))
}

case class FirebaseSubscriptionId(value: UUID) {
  def serialize: String = value.toString

  def asUnparsedFirebaseSubscriptionId: UnparsedFirebaseSubscriptionId =
    UnparsedFirebaseSubscriptionId(Id.validate(serialize).toOption.get)
}

case class DeviceClientId(value: String) extends AnyVal

case class FirebaseToken(value: String) extends AnyVal

case class FirebaseSubscriptionExpiredTime(value: ZonedDateTime) {
  def isAfter(date: ZonedDateTime): Boolean = value.isAfter(date)

  def isBefore(date: ZonedDateTime): Boolean = value.isBefore(date)
}

case class FirebaseSubscriptionCreationRequest(deviceClientId: DeviceClientId,
                                               token: FirebaseToken,
                                               expires: Option[FirebaseSubscriptionExpiredTime] = None,
                                               types: Seq[TypeName]) {

  def validate: Either[IllegalArgumentException, FirebaseSubscriptionCreationRequest] =
    validateTypes

  private def validateTypes: Either[IllegalArgumentException, FirebaseSubscriptionCreationRequest] =
    if (types.isEmpty) {
      scala.Left(new IllegalArgumentException("types must not be empty"))
    } else {
      Right(this)
    }
}

object FirebaseSubscription {
  val EXPIRES_TIME_MAX_DAY: Int = 7
  val allProperties: Properties = Properties("id", "deviceClientId", "expires", "types")
  val idProperty: Properties = Properties("id")

  def from(creationRequest: FirebaseSubscriptionCreationRequest,
           expireTime: FirebaseSubscriptionExpiredTime): FirebaseSubscription =
    FirebaseSubscription(id = FirebaseSubscriptionId.generate(),
      deviceClientId = creationRequest.deviceClientId,
      token = creationRequest.token,
      expires = expireTime,
      types = creationRequest.types)
}

case class FirebaseSubscription(id: FirebaseSubscriptionId,
                                deviceClientId: DeviceClientId,
                                token: FirebaseToken,
                                expires: FirebaseSubscriptionExpiredTime,
                                types: Seq[TypeName]) {
  def withTypes(types: Seq[TypeName]): FirebaseSubscription = copy(types = types)

  def withExpires(expires: FirebaseSubscriptionExpiredTime): FirebaseSubscription = copy(expires = expires)
}

case class FirebaseSubscriptionNotFoundException(id: FirebaseSubscriptionId) extends RuntimeException

case class ExpireTimeInvalidException(expires: ZonedDateTime, message: String) extends IllegalStateException(s"`${expires.format(TIME_FORMATTER)}` $message")

case class DeviceClientIdInvalidException(deviceClientId: DeviceClientId, message: String) extends IllegalArgumentException(s"`${deviceClientId.value}` $message")

case class TokenInvalidException(message: String) extends IllegalArgumentException(message)

case class MissingOrInvalidFirebaseCredentialException(message: String) extends IllegalArgumentException(message)

case class UnparsedFirebaseSubscriptionId(id: Id)

case class FirebaseSubscriptionIds(list: List[UnparsedFirebaseSubscriptionId])

case class FirebaseSubscriptionGetRequest(ids: Option[FirebaseSubscriptionIds],
                                          properties: Option[Properties]) extends WithoutAccountId {

  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(FirebaseSubscription.allProperties)
      case Some(value) =>
        value -- FirebaseSubscription.allProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ FirebaseSubscription.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] do not exist."))
        }
    }
}

object FirebaseSubscriptionGetResponse {
  def from(list: Seq[FirebaseSubscription], requestIds: Option[FirebaseSubscriptionIds]): FirebaseSubscriptionGetResponse =
    requestIds match {
      case None => FirebaseSubscriptionGetResponse(list.toList)
      case Some(value) =>
        FirebaseSubscriptionGetResponse(
          list = list.filter(subscription => value.list.contains(subscription.id.asUnparsedFirebaseSubscriptionId)),
          notFound = value.list.diff(list.map(_.id.asUnparsedFirebaseSubscriptionId)).toSet)
    }
}

case class FirebaseSubscriptionGetResponse(list: Seq[FirebaseSubscription],
                                           notFound: Set[UnparsedFirebaseSubscriptionId] = Set())

case class FirebaseSubscriptionCreationId(id: Id) {
  def serialise: String = id.value
}


case class FirebaseSubscriptionCreationResponse(id: FirebaseSubscriptionId,
                                                expires: Option[FirebaseSubscriptionExpiredTime])


case class FirebaseSubscriptionSetResponse(created: Option[Map[FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationResponse]],
                                           notCreated: Option[Map[FirebaseSubscriptionCreationId, SetError]],
                                           updated: Option[Map[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse]],
                                           notUpdated: Option[Map[UnparsedFirebaseSubscriptionId, SetError]],
                                           destroyed: Option[Seq[FirebaseSubscriptionId]],
                                           notDestroyed: Option[Map[UnparsedFirebaseSubscriptionId, SetError]])

object FirebaseSubscriptionCreationParseException {
  def from(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): FirebaseSubscriptionCreationParseException =
    FirebaseSubscriptionCreationParseException(standardError(errors))
}

case class FirebaseSubscriptionCreationParseException(setError: SetError) extends Exception

object FirebaseSubscriptionCreation {
  private val serverSetProperty: Set[String] = Set("id")
  private val assignableProperties: Set[String] = Set("deviceClientId", "token", "expires", "types")
  private val knownProperties: Set[String] = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[FirebaseSubscriptionCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(FirebaseSubscriptionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(FirebaseSubscriptionCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

case class FirebasePatchUpdateValidationException(error: String, property: Option[String] = None) extends IllegalArgumentException {
  def asProperty: Option[Properties] = property.map(value => Properties.toProperties(Set(value)))
}

sealed trait FirebaseSubscriptionUpdateResult

object FirebaseSubscriptionUpdateFailure {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[FirebaseSubscriptionSetUpdatePerformer])
}

case class FirebaseSubscriptionUpdateFailure(id: UnparsedFirebaseSubscriptionId, exception: Throwable) extends FirebaseSubscriptionUpdateResult {
  def asSetError: SetError = exception match {
    case e: FirebasePatchUpdateValidationException => SetError.invalidArguments(SetErrorDescription(e.error), e.asProperty)
    case e: FirebaseSubscriptionNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
    case e: ExpireTimeInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties.toProperties(Set("expires"))))
    case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage), None)
    case _ => {
      LOGGER.warn("Could not update firebase subscription request", exception)
      SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
}

case class FirebaseSubscriptionUpdateSuccess(id: FirebaseSubscriptionId, newExpires: Option[FirebaseSubscriptionExpiredTime] = None) extends FirebaseSubscriptionUpdateResult

case class FirebaseSubscriptionUpdateResults(results: Seq[FirebaseSubscriptionUpdateResult]) {
  def updated: Map[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse] =
    results.flatMap(result => result match {
      case success: FirebaseSubscriptionUpdateSuccess => Some((success.id, FirebaseSubscriptionUpdateResponse(success.newExpires)))
      case _ => None
    }).toMap

  def notUpdated: Map[UnparsedFirebaseSubscriptionId, SetError] = results.flatMap(result => result match {
    case failure: FirebaseSubscriptionUpdateFailure => Some(failure.id, failure.asSetError)
    case _ => None
  }).toMap
}

object FirebaseSubscriptionPatchObject {
  private val updateProperties: Set[String] = Set("types", "expires")
}

case class FirebaseSubscriptionPatchObject(value: Map[String, JsValue]) {

  def validate(typeStateFactory: TypeStateFactory): Either[FirebasePatchUpdateValidationException, ValidatedFirebaseSubscriptionPatchObject] =
    for {
      validatedProperties <- validateProperties
      types <- validateTypes(typeStateFactory)
      expires <- validateExpires
    } yield {
      ValidatedFirebaseSubscriptionPatchObject.from(types, expires)
    }

  def validateProperties: Either[FirebasePatchUpdateValidationException, FirebaseSubscriptionPatchObject] =
    value.find(pair => !updateProperties.contains(pair._1))
      .map(e => scala.Left(FirebasePatchUpdateValidationException("Some unknown properties were specified", Some(e._1))))
      .getOrElse(scala.Right(this))

  def validateTypes(typeStateFactory: TypeStateFactory): Either[FirebasePatchUpdateValidationException, List[TypeName]] =
    value.get("types") match {
      case Some(jsValue) => jsValue match {
        case JsArray(arrayValue) => arrayValue.toList
          .map(typeValue => parseType(typeValue, typeStateFactory))
          .traverse(x => Validated.fromEither(x.left.map(List(_)))).toEither
          .left.map(_.head)
          .flatMap(validateEmptyArrayTypes)
        case _ => Left(FirebasePatchUpdateValidationException("Expecting an array of JSON strings as an argument", Some("types")))
      }
      case None => scala.Right(List())
    }

  def validateExpires: Either[FirebasePatchUpdateValidationException, Option[FirebaseSubscriptionExpiredTime]] =
    value.get("expires") match {
      case Some(jsValue) => {
        jsValue match {
          case JsString(aString) => Try(ZonedDateTime.parse(aString)) match {
            case Success(value) => scala.Right(Some(FirebaseSubscriptionExpiredTime(UTCDate(value).asUTC)))
            case Failure(e) => scala.Left(FirebasePatchUpdateValidationException("This string can not be parsed to UTCDate", Some("expires")))
          }
          case _ => Left(FirebasePatchUpdateValidationException("Expecting a JSON string as an argument", Some("expires")))
        }
      }
      case None => scala.Right(None)
    }

  def validateEmptyArrayTypes(list: List[TypeName]): Either[FirebasePatchUpdateValidationException, List[TypeName]] =
    if (list.isEmpty) {
      scala.Left(FirebasePatchUpdateValidationException("Must not empty", Some("types")))
    } else {
      scala.Right(list)
    }

  private def parseType(jsValue: JsValue, typeStateFactory: TypeStateFactory): Either[FirebasePatchUpdateValidationException, TypeName] = jsValue match {
    case JsString(aString) => typeStateFactory.strictParse(aString).left.map(e => FirebasePatchUpdateValidationException(e.getMessage, Some("types")))
    case _ => Left(FirebasePatchUpdateValidationException("Expecting an array of JSON strings as an argument", Some("types")))
  }
}

object ValidatedFirebaseSubscriptionPatchObject {
  def from(typeNames: List[TypeName], expires: Option[FirebaseSubscriptionExpiredTime]): ValidatedFirebaseSubscriptionPatchObject =
    if (typeNames.isEmpty) {
      ValidatedFirebaseSubscriptionPatchObject(expiresUpdate = expires)
    } else {
      ValidatedFirebaseSubscriptionPatchObject(Some(typeNames.toSet), expiresUpdate = expires)
    }
}

case class ValidatedFirebaseSubscriptionPatchObject(typeUpdate: Option[Set[TypeName]] = None,
                                                    expiresUpdate: Option[FirebaseSubscriptionExpiredTime] = None)

case class FirebaseSubscriptionSetRequest(create: Option[Map[FirebaseSubscriptionCreationId, JsObject]],
                                          update: Option[Map[UnparsedFirebaseSubscriptionId, FirebaseSubscriptionPatchObject]],
                                          destroy: Option[Seq[UnparsedFirebaseSubscriptionId]]) extends WithoutAccountId

case class FirebaseSubscriptionUpdateResponse(expires: Option[FirebaseSubscriptionExpiredTime] = None)
