// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringEscapeUtils
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{Lock, Task}
import org.maproulette.models.dal.TaskDAL
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller}

/**
  * @author cuthbertm
  */
class MappingController @Inject() (sessionManager:SessionManager,
                                   taskDAL: TaskDAL) extends Controller {

  /**
    * Will return the specific geojson for the requested task
    *
    * @param taskId The id of the task that contains the geojson
    * @return The geojson
    */
  def getTaskDisplayGeoJSON(taskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(getResponseJSONNoLock(taskDAL.retrieveById(taskId)))
    }
  }

  /**
    * Gets a random next task based on the user selection criteria, which contains a lot of
    * different criteria for the search.
    *
    * @return
    */
  def getRandomNextTask(proximityId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(getResponseJSONNoLock(taskDAL.getRandomTasks(User.userOrMocked(user), params, 1, None, Utils.negativeToOption(proximityId)).headOption))
      }
    }
  }

  /**
    * Gets a random next task based on the user selection criteria, which contains a lot of
    * different criteria for the search. Includes Priority
    *
    * @return
    */
  def getRandomNextTaskWithPriority(proximityId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(getResponseJSONNoLock(taskDAL.getRandomTasksWithPriority(User.userOrMocked(user), params, 1, Utils.negativeToOption(proximityId)).headOption))
      }
    }
  }

  /**
    * Retrieve the JSON for the next task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialNextTask(parentId:Long, currentTaskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(getResponseJSON(taskDAL.getNextTaskInSequence(parentId, currentTaskId, Some(params.taskStatus.getOrElse(List.empty)))))
      }
    }
  }

  /**
    * Retrieve the JSON for the previous task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialPreviousTask(parentId:Long, currentTaskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(getResponseJSON(taskDAL.getPreviousTaskInSequence(parentId, currentTaskId, Some(params.taskStatus.getOrElse(List.empty)))))
      }
    }
  }

  private def getResponseJSONNoLock(task:Option[Task]) : JsValue = task match {
    case Some(t) => getResponseJSON(Some(t, Lock.emptyLock))
    case None => getResponseJSON(None)
  }

  /**
    * Builds the response JSON for mapping based on a Task
    *
    * @param task The optional task to check
    * @return If None supplied as Task parameter then will throw NotFoundException
    */
  private def getResponseJSON(task:Option[(Task, Lock)]) : JsValue = task match {
    case Some(t) =>
      val currentStatus = t._1.status.getOrElse(Task.STATUS_CREATED)
      val locked = t._2.lockedTime match {
        case Some(_) => true
        case None => false
      }
      val userString = taskDAL.getLastModifiedUser(null, t._1.id).headOption match {
        case Some(user) =>
          s"""
             |   "last_modified_user_osm_id":${user.osmProfile.id},
             |   "last_modified_user_id":${user.id},
             |   "last_modified_user":"${user.osmProfile.displayName}",
           """.stripMargin
        case None => ""
      }
      Json.parse(
        s"""
           |{
           |   "id":${t._1.id},
           |   "parentId":${t._1.parent},
           |   "name":"${t._1.name}",
           |   "instruction":"${StringEscapeUtils.escapeJson(t._1.instruction.getOrElse(""))}",
           |   "statusName":"${Task.getStatusName(currentStatus).getOrElse(Task.STATUS_CREATED_NAME)}",
           |   "status":$currentStatus, $userString
           |   "geometry":${t._1.geometries},
           |   "locked":$locked,
           |   "created":"${t._1.created}",
           |   "modified":"${t._1.modified}"
           |}
            """.stripMargin)
    case None => throw new NotFoundException(s"Could not find task")
  }
}
