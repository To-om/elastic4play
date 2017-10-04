package org.elastic4play.controllers

import javax.inject.{ Inject, Singleton }

import org.elastic4play.services.MigrationSrv
import play.api.mvc.{ AbstractController, ControllerComponents }

import scala.concurrent.ExecutionContext

/**
 * Migration controller : start migration process
 */
@Singleton
class MigrationCtrl @Inject() (
  components: ControllerComponents,
  apiMethod: ApiMethod,
  migrationSrv: MigrationSrv,
  implicit val ec: ExecutionContext) extends AbstractController(components) {

  def migrate = apiMethod("Migrate database")
    .async { implicit request ⇒
      migrationSrv.migrate.map(_ ⇒ NoContent)
    }
}
