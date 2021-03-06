package workshop

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

// This class runs a Simulation form within the IDE
object GatlingRunner {

  def main(args: Array[String]): Unit = {
    val simClass = classOf[WebShopSimulation].getName
    val props = new GatlingPropertiesBuilder
    props.simulationClass(simClass)
    Gatling.fromMap(props.build)
  }

}
