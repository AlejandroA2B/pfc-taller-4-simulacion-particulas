package taller

import common._
import org.scalameter._

/**
 * Taller 4 — simulación de partículas repartida entre varios núcleos.
 *
 * Complete cada función. Las firmas y los tipos no se modifican.
 *
 * Las abstracciones `parallel` y `task` vienen en el paquete `common`, que ya
 * está en el proyecto.
 */
class SimulacionParticulas {

  type Sistema = Array[Particula]

  // Punto 1: un paso de la simulación -----------------------------------------

  /** La posición nueva es la anterior más la velocidad por dt. */
  def mover(p: Particula, dt: Double): Particula = {
    p.copy(pos= p.pos+p.vel*dt)//Posicion antigua mas la velocidad multiplicada por dt
  }

  /** Avanza todo el sistema un paso de tiempo dt. */
  def pasoSecuencial(s: Sistema, dt: Double): Sistema = {
    pasoSegmento(s, dt, 0, s.length)
  }

  /** Avanza solo el tramo entre `desde` incluido y `hasta` excluido. */
  def pasoSegmento(s: Sistema, dt: Double, desde: Int, hasta: Int): Sistema = {
    val tramo = s.slice(desde, hasta)//Me trae un tramo del array
    tramo.map(q => mover(q, dt))//Aplico mover a cada particula del tramo
  }

  /**
   * Parte el sistema por la mitad con `parallel` mientras el tramo tenga más
   * elementos que el umbral, y por debajo de esa cifra resuelve en secuencial.
   */
  def pasoParalelo(s: Sistema, dt: Double, umbral: Int): Sistema = {
    def aux(desde: Int, hasta: Int): Sistema = {
      val distancia = hasta - desde//Calculo distancia
      if (distancia <= umbral || distancia <= 1) { //la distancia debe ser menor o igual al umbral establecido
        pasoSegmento(s, dt, desde, hasta)
      } else {
        val mitad = desde + distancia / 2
        val (izq, der) =
          parallel( //Al mismo tiempo se ejecutan las llamadas de la derecha y las de la izq
            aux(desde, mitad),
            aux(mitad, hasta)
          )
        izq ++ der//Se combina la respuesta con la izq con la derecha
      }
    }
    aux(0, s.length)
}

  // Punto 2: magnitudes globales ----------------------------------------------

  /** La suma de las energías cinéticas, media masa por velocidad al cuadrado. */
  def energiaSecuencial(s: Sistema): Double = {
    s.map{
      particula=>
      val calculoEnergia= 0.5 * particula.masa * particula.vel.norma * particula.vel.norma
      calculoEnergia
    }.sum //Suma todos los calculos de energia

  }

  /** La suma de las masas. */
  def masaTotal(s: Sistema): Double = {
    s.map(q=> q.masa).sum //Suma las masas en total
  }

  /**
   * La suma de las posiciones ponderadas por la masa, dividida entre la masa
   * total. Sin masa, el origen.
   */
  def centroDeMasaSecuencial(s: Sistema): Vector2 = {
    val Totalmasa = masaTotal(s)
    if (Totalmasa == 0.0) {
      Vector2(0, 0)//Devuelve origen
    } else {
      val suma = s.map(p => p.pos * p.masa).foldLeft(Vector2(0, 0))(_ + _)
      suma * (1.0 / Totalmasa)
    }

  }

  // Punto 3: las mismas magnitudes en paralelo --------------------------------

  /** La energía del sistema, repartida con el mismo umbral del punto 1. */
  def energiaParalela(s: Sistema, umbral: Int): Double = {
    def aux(desde: Int, hasta: Int): Double = {
      val n = hasta - desde
      if (n <= umbral || n <= 1) {
        s.slice(desde, hasta).map(p => 0.5 * p.masa * p.vel.norma * p.vel.norma).sum
      } else {
        val mitad = desde + n / 2
        val (izq, der) =
          parallel(  //Se ejecuta al mismo tiempo tanto la mitad izq como la mitad der
          aux(desde, mitad),
          aux(mitad, hasta))
        izq + der//Se juntan los resultados de la parte izq y derecha
      }
    }
    aux(0, s.length)//Llamo funcion aux que finalmente devuelve el resultado
  }

  /**
   * El centro de masa, repartido. Promediar los promedios de cada mitad da
   * otra cosa cuando las dos mitades no pesan igual.
   */
  def centroDeMasaParalelo(s: Sistema, umbral: Int): Vector2 = {

    def aux(desde: Int, hasta: Int): (Vector2, Double) = {
      val n = hasta - desde
      if (n <= umbral || n <= 1) {
        val tramo = s.slice(desde, hasta)
        val suma = tramo.map(p => p.pos * p.masa).foldLeft(Vector2(0, 0))(_ + _)
        val masa = tramo.map(_.masa).sum
        (suma, masa)//Devuelve una tupla
      } else {
        val mitad = desde + n / 2 //Se parte en 2
        val (izq, der) =
          parallel( //Se ejecuta al mismo tiempo tanto la mitad izq como la mitad der
            aux(desde, mitad),
            aux(mitad, hasta))
        (izq._1 + der._1, izq._2 + der._2)//Se juntan el resultado de todas las particiones
      }
    }
    val (suma, masa) = aux(0, s.length) //Guarda la tupla que devuelve aux una con la suma y otra con la masa
    if (masa == 0.0) {
      Vector2(0, 0)//origen
    } else {
      suma * (1.0 / masa)
    }

  }

  // Punto 4: trabajo y profundidad --------------------------------------------

  /** El número total de operaciones, una por partícula. */
  def trabajo(n: Int): Int = {
    n//Devuelve el total de operaciones pasado por parametro
  }

  /** Cuántas veces se parte a la mitad, más el tramo que queda sin partir. */
  def profundidad(n: Int, umbral: Int): Int = {
    if (n <= umbral || n <= 1) {
      n//Se llega al caso base que seria donde n ya es menor o igual al umbral.Ya con eso se arranca el proceso de subida
    } else {
      1 + profundidad((n + 1) / 2, umbral)//Cuenta las veces en que se parte n. se van acumulando los marcos de pila
    }
  }

  /** La profundidad más el trabajo entre p procesadores. */
  def tiempoEstimado(n: Int, p: Int, umbral: Int): Double = {
    val estimatedTime= profundidad(n,umbral)+trabajo(n).toDouble/p
    estimatedTime//Devuelve tiempo estimado
  }

  // Punto 5: medir la aceleración ---------------------------------------------

  /** Mediana de varias mediciones de ScalaMeter, en milisegundos. */
  def medir[A](repeticiones: Int)(cuerpo: => A): Double = {
    val muestras = Vector.fill(repeticiones) {
      (withWarmer(new Warmer.Default) measure { cuerpo }).value
    }
    muestras.sorted.apply(muestras.length / 2)
  }

  /** El cociente entre el tiempo secuencial y el paralelo. */
  def aceleracion(tSecuencial: Double, tParalelo: Double): Double = {
    val aceleration=tSecuencial/tParalelo
    aceleration
  }

  /**
   * El umbral que menos tarda de una lista de candidatos. Con la lista vacía,
   * el tamaño del sistema, que equivale a no repartir.
   */
  def umbralConveniente(s: Sistema, dt: Double, umbrales: List[Int]): Int = {
    if (umbrales.isEmpty) {
      s.length
    } else {
      val tiempos = umbrales.map { umb =>
        val tiempo = medir(3) {
          pasoParalelo(s, dt, umb)
        }

        (umb, tiempo)
      }
      tiempos.minBy(_._2)._1//Devuelve el umbral del par con menor tiempo
    }

  }
}
