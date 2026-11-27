package taller
import common._
import org.scalameter._
/**
 * El punto 5 pide medir aquí, sobre un sistema de al menos un millón de
 * partículas, el tiempo de `pasoSecuencial` y el de `pasoParalelo` con varios
 * umbrales, y reportar la aceleración de cada uno.
 *
 * Lo que hay ahora es el arranque mínimo sobre dos partículas.
 */
object App {

  def main(args: Array[String]): Unit = {
    val sim = new SimulacionParticulas()
    import sim._

    val nucleos = Runtime.getRuntime.availableProcessors
    val dt = 0.01

    // Mide un sistema con una lista de umbrales y escribe tablas listas para el informe.
    def experimento(titulo: String, sis: Sistema, umbrales: List[Int]): Unit = {
      val n = sis.length
      val mSec = muestrear(5)(pasoSecuencial(sis, dt))
      val tSec = mediana(mSec)

      println(s"\n## $titulo (n = $n, núcleos = $nucleos)")
      println(f"Secuencial: $tSec%.4f ms (desv. ${desviacion(mSec)}%.4f)")
      println("| Umbral | Paralela (ms) | Desv. | Aceleración | Aceleración estimada |")
      println("|---:|---:|---:|---:|---:|")

      umbrales.foreach { umbral =>
        val m = muestrear(5)(pasoParalelo(sis, dt, umbral))
        val tPar = mediana(m)
        val s = aceleracion(tSec, tPar)
        val sEst = n.toDouble / tiempoEstimado(n, nucleos, umbral)
        val marca = if (umbral >= n) " (control)" else ""
        println(f"| $umbral$marca | $tPar%.4f | ${desviacion(m)}%.4f | $s%.2f | $sEst%.2f |")
      }

      // Segundo secuencial al final: si difiere mucho del primero, la máquina se movió durante la corrida.
      val tSecFinal = mediana(muestrear(5)(pasoSecuencial(sis, dt)))
      println(f"Secuencial al final de la corrida: $tSecFinal%.4f ms")

      println(s"Umbral conveniente: ${umbralConveniente(sis, dt, umbrales)}")
    }

    // Comprobación rápida del sistema de ejemplo
    val sistema: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(1, 1), 1.0),
      Particula(Vector2(2, 0), Vector2(0, 1), 2.0))

    println(s"Núcleos disponibles: $nucleos")
    println(pasoSecuencial(sistema, 0.5).mkString(", "))

    // Sistema grande: un millón de partículas
    val s1: Sistema = Array.tabulate(1000000) { i =>
      Particula(Vector2(i.toDouble, i.toDouble), Vector2(1.0, 2.0), 1.0)
    }

    val umbrales = List(
      1,
      100,
      250,
      500,
      750,
      1000,
      1500,
      2000,
      2500,
      3000,
      4000,
      5000,
      7500,
      10000,
      s1.length)

    experimento("Sistema grande", s1, umbrales)

    //Sistema pequeño: unos pocos miles de partículas
    val s2: Sistema = Array.tabulate(4000) { i =>
      Particula(Vector2(i.toDouble, i.toDouble), Vector2(1.0, 2.0), 1.0)
    }

    val umbralesPequeno = List(1, 10, 50, 100, 250, 500, 1000, 2000, s2.length)

    experimento("Sistema pequeño", s2, umbralesPequeno) //Llamo a experimento para realizar la ejecucion de las medidas
  }

  // Funciones que ayudan a la medición
  //Ejecuta una operacion las veces que se le indique y guarda en un vector los tiempos de ejecucion
  def muestrear[A](repeticiones: Int)(cuerpo: => A): Vector[Double] =
    Vector.fill(repeticiones) {
      (withWarmer(new Warmer.Default) measure { cuerpo }).value
    }
//Calcula mediana de los tiempos
  def mediana(v: Vector[Double]): Double =
    v.sorted.apply(v.length / 2)
  //Calcula desviacion estandar de los tiempos
  def desviacion(v: Vector[Double]): Double = {
    val media = v.sum / v.size
    math.sqrt(v.map(x => (x - media) * (x - media)).sum / v.size)
  }

}
