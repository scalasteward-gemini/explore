// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package explore

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import org.scalajs.dom
import scala.scalajs.js
import js.annotation._
import japgolly.scalajs.react.extra.router._

@JSExportTopLevel("Explore")
object ExploreMain extends IOApp {

  @JSExport
  def runIOApp(): Unit = main(Array.empty)

  override def run(args: List[String]): IO[ExitCode] = IO {

    val container = Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }

    val router = Router(BaseUrl.fromWindowOrigin, Routing.config)
    router().renderIntoDOM(container)

    explore.model.AppState.pollClient.subscribe[IO, explore.model.Task](
      "subscription { poll_results {option_id option { id text } votes}}").unsafeRunAsyncAndForget()

    ExitCode.Success
  }
}
