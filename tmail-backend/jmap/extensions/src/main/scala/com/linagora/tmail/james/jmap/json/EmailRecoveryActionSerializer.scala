/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{EmailRecoveryAction, EmailRecoveryActionCreationId, EmailRecoveryActionCreationRequest, EmailRecoveryActionCreationResponse, EmailRecoveryActionGetRequest, EmailRecoveryActionGetResponse, EmailRecoveryActionIds, EmailRecoveryActionSetRequest, EmailRecoveryActionSetResponse, EmailRecoveryActionUpdatePatchObject, EmailRecoveryActionUpdateRequest, EmailRecoveryActionUpdateResponse, EmailRecoveryActionUpdateStatus, EmailRecoveryDeletedAfter, EmailRecoveryDeletedBefore, EmailRecoveryHasAttachment, EmailRecoveryReceivedAfter, EmailRecoveryReceivedBefore, EmailRecoveryRecipient, EmailRecoverySender, EmailRecoverySubject, ErrorRestoreCount, SuccessfulRestoreCount, UnparsedEmailRecoveryActionId}
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.{jsObjectReads, mapWrites}
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import play.api.libs.json._

object EmailRecoveryActionSerializer {

  private implicit val creationIdReads: Reads[EmailRecoveryActionCreationId] = Json.valueReads[EmailRecoveryActionCreationId]

  private implicit val creationIdWrite: Writes[EmailRecoveryActionCreationId] = value => JsString(value.id.value)

  private implicit val mapCreationRequest: Reads[Map[EmailRecoveryActionCreationId, JsObject]] =
    Reads.mapReads[EmailRecoveryActionCreationId, JsObject] {
      string => creationIdReads.reads(JsString(string))
    }

  private implicit val emailRecoveryActionUpdatePatchObjectReads: Reads[EmailRecoveryActionUpdatePatchObject] = {
    case jsObject: JsObject => JsSuccess(EmailRecoveryActionUpdatePatchObject(jsObject))
    case _ => JsError("EmailRecoveryActionUpdatePatchObject needs to be represented by a JsObject")
  }

  private implicit val mapUpdateRequest: Reads[Map[UnparsedEmailRecoveryActionId, EmailRecoveryActionUpdatePatchObject]] =
    Reads.mapReads[UnparsedEmailRecoveryActionId, EmailRecoveryActionUpdatePatchObject](
      string => Json.valueReads[UnparsedEmailRecoveryActionId].reads(JsString(string))) {
      emailRecoveryActionUpdatePatchObjectReads
    }

  private implicit val emailRecoveryDeletedBeforeReads: Reads[EmailRecoveryDeletedBefore] = Json.valueReads[EmailRecoveryDeletedBefore]
  private implicit val emailRecoveryDeletedAfterReads: Reads[EmailRecoveryDeletedAfter] = Json.valueReads[EmailRecoveryDeletedAfter]
  private implicit val emailRecoveryReceivedBeforeReads: Reads[EmailRecoveryReceivedBefore] = Json.valueReads[EmailRecoveryReceivedBefore]
  private implicit val emailRecoveryReceivedAfterReads: Reads[EmailRecoveryReceivedAfter] = Json.valueReads[EmailRecoveryReceivedAfter]
  private implicit val emailRecoveryHasAttachmentReads: Reads[EmailRecoveryHasAttachment] = Json.valueReads[EmailRecoveryHasAttachment]
  private implicit val emailRecoverySubjectReads: Reads[EmailRecoverySubject] = Json.valueReads[EmailRecoverySubject]
  private implicit val emailRecoverySenderReads: Reads[EmailRecoverySender] = mailAddressReads.map(EmailRecoverySender)
  private implicit val emailRecoveryRecipientReads: Reads[EmailRecoveryRecipient] = Json.valueReads[EmailRecoveryRecipient]
  private implicit val emailRecoveryActionCreationRequestReads: Reads[EmailRecoveryActionCreationRequest] = Json.reads[EmailRecoveryActionCreationRequest]

  private implicit val emailRecoveryActionUpdateStatusReads: Reads[EmailRecoveryActionUpdateStatus] = Json.valueReads[EmailRecoveryActionUpdateStatus]
  private implicit val emailRecoveryActionUpdateRequestReads: Reads[EmailRecoveryActionUpdateRequest] = Json.reads[EmailRecoveryActionUpdateRequest]
  private implicit val taskIdWrites: Writes[TaskId] = value => JsString(value.asString())
  private implicit val emailRecoveryActionCreationResponseWrites: Writes[EmailRecoveryActionCreationResponse] = Json.writes[EmailRecoveryActionCreationResponse]
  private implicit val mapSetErrorForCreationWrites: Writes[Map[EmailRecoveryActionCreationId, SetError]] =
    mapWrites[EmailRecoveryActionCreationId, SetError](_.serialise, setErrorWrites)
  private implicit val mapSetErrorForUpdateWrites: Writes[Map[UnparsedEmailRecoveryActionId, SetError]] =
    mapWrites[UnparsedEmailRecoveryActionId, SetError](_.id.value, setErrorWrites)

  private implicit def emailRecoveryActionCreationResponseMapWrites(implicit emailRecoveryActionCreationResponseWrites: Writes[EmailRecoveryActionCreationResponse]): Writes[Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse]] =
    mapWrites[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse](_.id.value, emailRecoveryActionCreationResponseWrites)

  private implicit val emailRecoveryActionUpdateResponseWrites: Writes[EmailRecoveryActionUpdateResponse] = Json.valueWrites[EmailRecoveryActionUpdateResponse]

  private implicit def emailRecoveryActionUpdateResponseMapWrites: Writes[Map[TaskId, EmailRecoveryActionUpdateResponse]] =
    mapWrites[TaskId, EmailRecoveryActionUpdateResponse](taskId => taskId.asString(), emailRecoveryActionUpdateResponseWrites)

  private implicit val emailRecoveryActionSetResponseWrites: OWrites[EmailRecoveryActionSetResponse] = Json.writes[EmailRecoveryActionSetResponse]


  private implicit val emailRecoveryActionSetRequestReads: Reads[EmailRecoveryActionSetRequest] = Json.reads[EmailRecoveryActionSetRequest]

  private implicit val idFormat: Format[UnparsedEmailRecoveryActionId] = Json.valueFormat[UnparsedEmailRecoveryActionId]
  private implicit val emailRecoveryActionIdsReads: Reads[EmailRecoveryActionIds] = Json.valueReads[EmailRecoveryActionIds]
  private implicit val emailRecoveryActionGetRequestReads: Reads[EmailRecoveryActionGetRequest] = Json.reads[EmailRecoveryActionGetRequest]

  private implicit val successfulRestoreCountWrites: Writes[SuccessfulRestoreCount] = Json.valueWrites[SuccessfulRestoreCount]
  private implicit val errorRestoreCountWrites: Writes[ErrorRestoreCount] = Json.valueWrites[ErrorRestoreCount]
  private implicit val statusWrites: Writes[Status] = status => JsString(status.getValue)
  private implicit val emailRecoveryActionWrites: Writes[EmailRecoveryAction] = Json.writes[EmailRecoveryAction]
  private implicit val emailRecoveryActionIdsWrites: Writes[EmailRecoveryActionIds] = Json.writes[EmailRecoveryActionIds]
  private implicit val emailRecoveryActionGetResponseWrites: Writes[EmailRecoveryActionGetResponse] = Json.writes[EmailRecoveryActionGetResponse]

  def deserializeSetRequest(input: JsValue): JsResult[EmailRecoveryActionSetRequest] = Json.fromJson[EmailRecoveryActionSetRequest](input)

  def deserializeSetCreationRequest(input: JsValue): JsResult[EmailRecoveryActionCreationRequest] = Json.fromJson[EmailRecoveryActionCreationRequest](input)

  def deserializeSetUpdateRequest(input: JsValue): JsResult[EmailRecoveryActionUpdateRequest] = Json.fromJson[EmailRecoveryActionUpdateRequest](input)

  def deserializeGetRequest(input: JsValue): JsResult[EmailRecoveryActionGetRequest] = Json.fromJson[EmailRecoveryActionGetRequest](input)

  def serializeGetResponse(response: EmailRecoveryActionGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serializeSetResponse(response: EmailRecoveryActionSetResponse): JsObject = Json.toJsObject(response)

}
