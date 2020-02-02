package explore.polls

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common._
import explore.model.Poll
import explore.model.Actions.PollsActionsIO
import crystal.react.io.implicits._

final case class Polls(polls: List[Poll]) extends ReactProps {
  @inline def render: VdomElement = Polls.component(this)
}

object Polls {
  type Props = Polls

  private val component =
    ScalaComponent
      .builder[Props]("Polls")
      .render_P { props =>
        <.div(
          props.polls.toTagMod{ poll =>
            <.div(
              <.h2(poll.question),
              poll.options.toTagMod{ option =>
                <.span(
                  <.button(^.tpe := "button", option.text, ^.onClick --> PollsActionsIO.vote(option.id))
                )
              },
              <.div(PollResults(poll.id))
            )
          }
        )
      }
      .componentWillMount { _ =>
        PollsActionsIO.refresh()
      }
      .build  
}