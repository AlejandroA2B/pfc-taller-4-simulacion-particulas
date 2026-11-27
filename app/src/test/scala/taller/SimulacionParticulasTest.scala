package taller

import org.scalatest.funsuite.AnyFunSuite
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import scala.util.Random

/**
 * Las pruebas no miden tiempos: un tiempo depende de la máquina y de lo que
 * esté corriendo al lado, así que una prueba que lo compare fallaría sin que
 * nada esté mal.
 */
@RunWith(classOf[JUnitRunner])
class SimulacionParticulasTest extends AnyFunSuite {

  val sim = new SimulacionParticulas()
  import sim._

  val eps = 1e-9

  val p = Particula(Vector2(0, 0), Vector2(1, 2), 1.0)

  val s: Sistema = Array(
    Particula(Vector2(0, 0), Vector2(3, 4), 2.0), // norma 5
    Particula(Vector2(4, 0), Vector2(0, 0), 2.0))

  val vacio: Sistema = Array()

  val azar = new Random(7)
  val grande: Sistema = Array.fill(1000)(Particula(
    Vector2(azar.nextDouble() * 10, azar.nextDouble() * 10),
    Vector2(azar.nextDouble() * 2, azar.nextDouble() * 2),
    azar.nextDouble() * 5 + 0.1))

  def cerca(a: Double, b: Double): Boolean = Math.abs(a - b) < 1e-6

  def cerca(a: Vector2, b: Vector2): Boolean =
    Math.abs(a.x - b.x) < 1e-6 && Math.abs(a.y - b.y) < 1e-6

  // Punto 1 -------------------------------------------------------------------

  test("pasoSecuencial: la posición avanza velocidad por dt") {
    assert(pasoSecuencial(Array(p), 2.0).head.pos == Vector2(2.0, 4.0))
  }

  test("pasoSecuencial: con dt cero nada se mueve") {
    assert(pasoSecuencial(Array(p), 0.0).head.pos == Vector2(0.0, 0.0))
  }

  test("pasoSecuencial: la velocidad y la masa no cambian") {
    val despues = pasoSecuencial(Array(p), 3.0).head
    assert(despues.vel == p.vel && despues.masa == p.masa)
  }

  test("pasoSecuencial: sobre el sistema vacío no hay nada que mover") {
    assert(pasoSecuencial(vacio, 1.0).isEmpty)
  }

  test("pasoSegmento: solo calcula el tramo pedido") {
    val tramo = pasoSegmento(s, 1.0, 1, 2)
    assert(tramo.length == 1 && tramo.head.pos == Vector2(4.0, 0.0))
  }

  test("pasoSegmento: el tramo vacío da un sistema vacío") {
    assert(pasoSegmento(s, 1.0, 1, 1).isEmpty)
  }

  test("pasoParalelo: da lo mismo que el secuencial en un caso simple") {
    assert(pasoParalelo(Array(p), 2.0, 1).head.pos == Vector2(2.0, 4.0))
  }

  test("pasoParalelo: coincide con el secuencial para varios umbrales") {
    val umbrales = List(1, 2, 7, 64, 1000, 5000)
    assert(umbrales.forall { u =>
      pasoParalelo(grande, 0.5, u).zip(pasoSecuencial(grande, 0.5))
        .forall { case (a, b) => cerca(a.pos, b.pos) }
    })
  }

  test("pasoParalelo: conserva el orden y el tamaño del sistema") {
    assert(pasoParalelo(grande, 0.5, 32).length == grande.length)
  }

  test("pasoParalelo: con umbral cero no se queda sin fondo") {
    assert(pasoParalelo(Array(p), 1.0, 0).length == 1)
  }

  //Mis pruebas paso secuencial
  test("pasoSecuencial: mueve cada partícula según su propia velocidad") {
    val s2 = Array(
      Particula(Vector2(0, 0), Vector2(2, 0), 1.0),
      Particula(Vector2(0, 0), Vector2(0, -3), 1.0))
    val r = pasoSecuencial(s2, 1.5)
    assert(cerca(r(0).pos, Vector2(3.0, 0.0)))
    assert(cerca(r(1).pos, Vector2(0.0, -4.5)))
  }

  test("pasoSecuencial: con dt negativo retrocede") {
    val r = pasoSecuencial(Array(p), -1.0).head
    assert(cerca(r.pos, Vector2(-1.0, -2.0)))
  }

  test("pasoSecuencial: no modifica el arreglo original") {
    val original = Array(p)
    pasoSecuencial(original, 5.0)
    assert(cerca(original.head.pos, Vector2(0.0, 0.0)))
  }

  test("pasoSecuencial: es lineal en dt (dos pasos de dt = un paso de 2dt)") {
    val a = pasoSecuencial(pasoSecuencial(Array(p), 1.0), 1.0).head.pos
    val b = pasoSecuencial(Array(p), 2.0).head.pos
    assert(cerca(a, b))
  }

  test("pasoSecuencial: conserva el orden de las partículas") {
    val sistema = Array(
      Particula(Vector2(1, 2), Vector2(2, 0), 1.0),
      Particula(Vector2(5, 3), Vector2(0, 4), 3.0),
      Particula(Vector2(-2, 1), Vector2(-1, 2), 5.0)
    )

    val r = pasoSecuencial(sistema, 2.0)

    assert(cerca(r(0).pos, Vector2(5, 2)))
    assert(cerca(r(1).pos, Vector2(5, 11)))
    assert(cerca(r(2).pos, Vector2(-4, 5)))
  }

  //Mis pruebas pasoSegemento

  test("pasoSegmento: tramo al inicio") {
    val r = pasoSegmento(grande, 0.0, 0, 10)
    assert(r.length == 10 && cerca(r.head.pos, grande.head.pos))
  }

  test("pasoSegmento: tramo al final") {
    val n = grande.length
    val r = pasoSegmento(grande, 0.0, n - 5, n)
    assert(r.length == 5 && cerca(r.last.pos, grande.last.pos))
  }

  test("pasoSegmento: tramo completo coincide con pasoSecuencial") {
    val a = pasoSegmento(grande, 0.3, 0, grande.length)
    val b = pasoSecuencial(grande, 0.3)
    assert(a.length == b.length && a.zip(b).forall { case (x, y) => cerca(x.pos, y.pos) })
  }

  test("pasoSegmento: tramos contiguos concatenados = sistema completo") {
    val mitad = grande.length / 2
    val izq = pasoSegmento(grande, 0.2, 0, mitad)
    val der = pasoSegmento(grande, 0.2, mitad, grande.length)
    val completo = izq ++ der
    val esperado = pasoSecuencial(grande, 0.2)
    assert(completo.length == esperado.length &&
      completo.zip(esperado).forall { case (x, y) => cerca(x.pos, y.pos) })
  }

  test("pasoSegmento: desde > hasta devuelve vacío") {
    assert(pasoSegmento(grande, 1.0, 5, 3).isEmpty)
  }

  test("pasoSegmento: devuelve exclusivamente las partículas del tramo indicado") {
    val r = pasoSegmento(grande, 1.0, 10, 20)

    assert(r.length == 10)

    assert(r.zipWithIndex.forall { case (particula, i) =>
      cerca(particula.pos, grande(i + 10).pos + grande(i + 10).vel)
    })
  }

  //Mis pruebas Paso Paralelo
  test("pasoParalelo: umbral mayor que n no reparte y coincide con secuencial") {
    val a = pasoParalelo(grande, 0.4, grande.length + 50)
    val b = pasoSecuencial(grande, 0.4)
    assert(a.length == b.length && a.zip(b).forall { case (x, y) => cerca(x.pos, y.pos) })
  }

  test("pasoParalelo: calcula correctamente dos segmentos diferentes") {
    val sistema = Array(
      Particula(Vector2(1, 2), Vector2(2, -1), 1.0),
      Particula(Vector2(-3, 4), Vector2(-1, 3), 2.0)
    )

    val r = pasoParalelo(sistema, 2.0, 1)

    assert(cerca(r(0).pos, Vector2(5, 0)))
    assert(cerca(r(1).pos, Vector2(-5, 10)))
  }

  test("pasoParalelo: no modifica el sistema original") {
    val sistema = Array(
      Particula(Vector2(1, 2), Vector2(3, 1), 2.0),
      Particula(Vector2(4, 5), Vector2(-2, 2), 3.0)
    )

    val original = sistema.clone()

    pasoParalelo(sistema, 2.0, 1)

    assert(sistema.indices.forall(i =>
      cerca(sistema(i).pos, original(i).pos)
    ))
  }

  test("pasoParalelo: dt cero deja el sistema igual") {
    val a = pasoParalelo(grande, 0.0, 16)
    assert(a.length == grande.length &&
      a.zip(grande).forall { case (x, y) => cerca(x.pos, y.pos) })
  }

  test("pasoParalelo: sistema vacío da vacío para cualquier umbral") {
    assert(pasoParalelo(Array(), 1.0, 1).isEmpty &&
      pasoParalelo(Array(), 1.0, 100).isEmpty)
  }

  test("pasoParalelo: conserva las propiedades de cada partícula") {
    val sistema = Array(
      Particula(Vector2(0, 0), Vector2(1, 2), 2.0),
      Particula(Vector2(3, 4), Vector2(-2, 1), 5.0),
      Particula(Vector2(6, 7), Vector2(0, -3), 8.0)
    )

    val r = pasoParalelo(sistema, 1.0, 1)

    assert(r.length == 3)
    assert(r(0).masa == 2.0 && r(1).masa == 5.0 && r(2).masa == 8.0)
    assert(cerca(r(0).vel, Vector2(1, 2)))
    assert(cerca(r(1).vel, Vector2(-2, 1)))
    assert(cerca(r(2).vel, Vector2(0, -3)))
  }

  // Punto 2 -------------------------------------------------------------------

  test("energiaSecuencial: media masa por velocidad al cuadrado") {
    assert(cerca(energiaSecuencial(s), 25.0))
  }

  test("energiaSecuencial: el sistema vacío no tiene energía") {
    assert(energiaSecuencial(vacio) == 0.0)
  }

  test("energiaSecuencial: una partícula quieta no aporta energía") {
    assert(cerca(energiaSecuencial(Array(s(1))), 0.0))
  }

  test("masaTotal: suma las masas") {
    assert(cerca(masaTotal(s), 4.0))
  }

  test("masaTotal: el sistema vacío no tiene masa") {
    assert(masaTotal(vacio) == 0.0)
  }

  test("centroDeMasaSecuencial: el punto medio de dos masas iguales") {
    assert(cerca(centroDeMasaSecuencial(s), Vector2(2.0, 0.0)))
  }

  test("centroDeMasaSecuencial: sin masa el centro es el origen") {
    assert(centroDeMasaSecuencial(vacio) == Vector2(0, 0))
  }

  test("centroDeMasaSecuencial: la masa mayor arrastra el centro") {
    val pesado: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 3.0),
      Particula(Vector2(4, 0), Vector2(0, 0), 1.0))
    assert(cerca(centroDeMasaSecuencial(pesado), Vector2(1.0, 0.0)))
  }

  //Mis test energiaSecuencial
  test("energiaSecuencial: escala con la masa") {
    val a = Array(Particula(Vector2(0,0), Vector2(2,0), 1.0))
    val b = Array(Particula(Vector2(0,0), Vector2(2,0), 4.0))
    assert(cerca(energiaSecuencial(b), 4.0 * energiaSecuencial(a)))
  }

  test("energiaSecuencial: escala con el cuadrado de la velocidad") {
    val a = Array(Particula(Vector2(0,0), Vector2(3,0), 1.0))
    val b = Array(Particula(Vector2(0,0), Vector2(6,0), 1.0))
    assert(cerca(energiaSecuencial(b), 4.0 * energiaSecuencial(a)))
  }

  test("energiaSecuencial: no depende de la posición") {
    val a = Array(Particula(Vector2(0,0),   Vector2(1,1), 2.0))
    val b = Array(Particula(Vector2(99,99), Vector2(1,1), 2.0))
    assert(cerca(energiaSecuencial(a), energiaSecuencial(b)))
  }

  test("energiaSecuencial: es aditiva al concatenar sistemas") {
    val a = Array(Particula(Vector2(0,0), Vector2(1,0), 1.0))
    val b = Array(Particula(Vector2(0,0), Vector2(0,2), 3.0))
    assert(cerca(energiaSecuencial(a ++ b),
      energiaSecuencial(a) + energiaSecuencial(b)))
  }

  test("energiaSecuencial: solo velocidades perpendiculares se suman como escalares") {
    // v=(3,4) y v=(4,-3) tienen la misma norma; la energía debe ser igual.
    val a = Array(Particula(Vector2(0,0), Vector2(3,4), 2.0))
    val b = Array(Particula(Vector2(0,0), Vector2(4,-3), 2.0))
    assert(cerca(energiaSecuencial(a), energiaSecuencial(b)))
  }

  test("energiaSecuencial: nunca es negativa") {
    assert(energiaSecuencial(grande) >= 0.0)
  }

  //Mis test masaTotal

  test("masaTotal: es aditiva al concatenar") {
    val mitad = grande.length / 2
    val a = grande.slice(0, mitad)
    val b = grande.slice(mitad, grande.length)
    assert(cerca(masaTotal(a ++ b), masaTotal(a) + masaTotal(b)))
  }

  test("masaTotal: masa cero si todas son cero") {
    val ceros = Array.fill(10)(Particula(Vector2(0,0), Vector2(1,1), 0.0))
    assert(masaTotal(ceros) == 0.0)
  }

  test("masaTotal: una partícula devuelve su masa") {
    assert(cerca(masaTotal(Array(Particula(Vector2(0,0), Vector2(0,0), 7.5))), 7.5))
  }

  test("masaTotal: no depende del orden") {
    val rev = grande.reverse
    assert(cerca(masaTotal(grande), masaTotal(rev)))
  }

  test("masaTotal: escalar todas las masas escala el total") {
    val doble = grande.map(q => q.copy(masa = 2.0 * q.masa))
    assert(cerca(masaTotal(doble), 2.0 * masaTotal(grande)))
  }

  test("masaTotal: es positiva si alguna masa es positiva") {
    assert(masaTotal(grande) > 0.0)
  }

  //Mis test centroDeMasaSecuencial
  test("centroDeMasaSecuencial: una sola partícula está en su posición") {
    val q = Particula(Vector2(3, -7), Vector2(0,0), 5.0)
    assert(cerca(centroDeMasaSecuencial(Array(q)), Vector2(3, -7)))
  }

  test("centroDeMasaSecuencial: calcula el centro con posiciones negativas") {
    val sistema = Array(
      Particula(Vector2(-4, -2), Vector2(0, 0), 2.0),
      Particula(Vector2(2, 4), Vector2(0, 0), 4.0)
    )

    assert(cerca(centroDeMasaSecuencial(sistema), Vector2(0, 2)))
  }

  test("centroDeMasaSecuencial: el centro conserva las coordenadas cuando todas las posiciones son iguales") {
    val sistema = Array(
      Particula(Vector2(7, -3), Vector2(1, 0), 1.0),
      Particula(Vector2(7, -3), Vector2(0, 2), 4.0),
      Particula(Vector2(7, -3), Vector2(-1, 1), 8.0)
    )

    assert(cerca(centroDeMasaSecuencial(sistema), Vector2(7, -3)))
  }

  test("centroDeMasaSecuencial: es combinación convexa (dentro del bounding box)") {
    val c = centroDeMasaSecuencial(grande)
    val xs = grande.map(_.pos.x); val ys = grande.map(_.pos.y)
    assert(c.x >= xs.min - eps && c.x <= xs.max + eps)
    assert(c.y >= ys.min - eps && c.y <= ys.max + eps)
  }

  test("centroDeMasaSecuencial: no cambia al reordenar") {
    assert(cerca(centroDeMasaSecuencial(grande), centroDeMasaSecuencial(grande.reverse)))
  }

  test("centroDeMasaSecuencial: trasladar todo traslada el centro") {
    val d = Vector2(5, -3)
    val movido = grande.map(q => q.copy(pos = q.pos + d))
    val c1 = centroDeMasaSecuencial(grande)
    val c2 = centroDeMasaSecuencial(movido)
    assert(cerca(c2, c1 + d))
  }



  // Punto 3 -------------------------------------------------------------------

  test("energiaParalela: coincide con la secuencial en el ejemplo") {
    assert(cerca(energiaParalela(s, 1), 25.0))
  }

  test("energiaParalela: coincide con la secuencial para varios umbrales") {
    val esperado = energiaSecuencial(grande)
    assert(List(1, 3, 64, 999, 5000).forall(u => cerca(energiaParalela(grande, u), esperado)))
  }

  test("energiaParalela: sobre el sistema vacío da cero") {
    assert(energiaParalela(vacio, 1) == 0.0)
  }

  test("centroDeMasaParalelo: coincide con el secuencial en el ejemplo") {
    assert(cerca(centroDeMasaParalelo(s, 1), Vector2(2.0, 0.0)))
  }

  test("centroDeMasaParalelo: coincide con el secuencial para varios umbrales") {
    val esperado = centroDeMasaSecuencial(grande)
    assert(List(1, 3, 64, 999, 5000).forall(u => cerca(centroDeMasaParalelo(grande, u), esperado)))
  }

  test("centroDeMasaParalelo: promediar promedios daría otra cosa") {
    // Tres partículas: dos en el origen y una lejos. El promedio de los
    // promedios de las dos mitades no es el centro de masa del conjunto.
    val tres: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(9, 0), Vector2(0, 0), 1.0))
    assert(cerca(centroDeMasaParalelo(tres, 1), Vector2(3.0, 0.0)))
  }

  test("centroDeMasaParalelo: sin masa el centro es el origen") {
    assert(centroDeMasaParalelo(vacio, 1) == Vector2(0, 0))
  }

  //Mis tests energiaParalela

  test("energiaParalela: umbral mayor que n coincide con secuencial") {
    assert(cerca(energiaParalela(grande, grande.length + 10),
      energiaSecuencial(grande)))
  }

  test("energiaParalela: calcula correctamente energías con masas y velocidades diferentes") {
    val sistema = Array(
      Particula(Vector2(0, 0), Vector2(3, 4), 2.0),
      Particula(Vector2(1, 1), Vector2(-4, 0), 3.0),
      Particula(Vector2(2, 2), Vector2(0, -2), 1.0)
    )

    assert(cerca(energiaParalela(sistema, 1), 51.0))
  }

  test("energiaParalela: es aditiva al concatenar") {
    val mitad = grande.length / 2
    val a = grande.slice(0, mitad); val b = grande.slice(mitad, grande.length)
    assert(cerca(energiaParalela(a ++ b, 7),
      energiaParalela(a, 7) + energiaParalela(b, 7)))
  }

  test("energiaParalela: las componentes negativas de velocidad también aportan energía") {
    val sistema = Array(
      Particula(Vector2(0, 0), Vector2(-3, 0), 2.0),
      Particula(Vector2(0, 0), Vector2(0, -4), 2.0)
    )

    assert(cerca(energiaParalela(sistema, 1), 25.0))
  }

  test("energiaParalela: una partícula quieta da cero") {
    val q = Particula(Vector2(9,9), Vector2(0,0), 4.0)
    assert(energiaParalela(Array(q), 1) == 0.0)
  }

  test("energiaParalela: todas quietas da cero") {
    val quietas = Array.fill(50)(Particula(Vector2(1,1), Vector2(0,0), 2.0))
    assert(energiaParalela(quietas, 4) == 0.0)
  }

  //Mis tests centroDeMasaParalelo

  test("centroDeMasaParalelo: umbral mayor que n coincide con secuencial") {
    assert(cerca(centroDeMasaParalelo(grande, grande.length + 10),
      centroDeMasaSecuencial(grande)))
  }

  test("centroDeMasaParalelo: calcula el centro con tres masas y coordenadas diferentes") {
    val sistema = Array(
      Particula(Vector2(-4, 2), Vector2(0, 0), 1.0),
      Particula(Vector2(2, -2), Vector2(0, 0), 2.0),
      Particula(Vector2(5, 4), Vector2(0, 0), 3.0)
    )

    assert(cerca(centroDeMasaParalelo(sistema, 1), Vector2(2.5, 10.0 / 6.0)))
  }

  test("centroDeMasaParalelo: una sola partícula está en su posición") {
    val q = Particula(Vector2(-4, 8), Vector2(0,0), 2.0)
    assert(cerca(centroDeMasaParalelo(Array(q), 1), Vector2(-4, 8)))
  }

  test("centroDeMasaParalelo: masas desiguales no se promedian como promedios") {
    // (0*1 + 12*3)/4 = 9 ; el promedio de promedios daría (0+12)/2 = 6
    val s2 = Array(
      Particula(Vector2(0, 0),  Vector2(0,0), 1.0),
      Particula(Vector2(12, 0), Vector2(0,0), 3.0))
    assert(cerca(centroDeMasaParalelo(s2, 1), Vector2(9, 0)))
  }

  test("centroDeMasaParalelo: conserva el centro cuando todas las partículas están en una posición común") {
    val sistema = Array(
      Particula(Vector2(-3, 8), Vector2(1, 0), 2.0),
      Particula(Vector2(-3, 8), Vector2(0, 1), 5.0),
      Particula(Vector2(-3, 8), Vector2(2, 2), 7.0)
    )

    assert(cerca(centroDeMasaParalelo(sistema, 1), Vector2(-3, 8)))
  }

  test("centroDeMasaParalelo: trasladar todo traslada el centro") {
    val d = Vector2(2, 6)
    val movido = grande.map(q => q.copy(pos = q.pos + d))
    val c1 = centroDeMasaParalelo(grande, 8)
    val c2 = centroDeMasaParalelo(movido, 8)
    assert(cerca(c2, c1 + d))
  }

  // Punto 4 -------------------------------------------------------------------

  test("trabajo: una operación por partícula") { assert(trabajo(1024) == 1024) }

  test("profundidad: si el umbral cubre todo, no se parte") {
    assert(profundidad(1024, 1024) == 1024)
  }

  test("profundidad: una partición y el tramo que queda") {
    assert(profundidad(1024, 512) == 513)
  }

  test("profundidad: diez particiones y un elemento") {
    assert(profundidad(1024, 1) == 11)
  }

  test("tiempoEstimado: profundidad más trabajo entre procesadores") {
    assert(cerca(tiempoEstimado(1024, 4, 1), 267.0))
  }

  test("tiempoEstimado: con un procesador no hay ganancia") {
    assert(cerca(tiempoEstimado(1024, 1, 1), 11.0 + 1024.0))
  }

  test("profundidad: nunca supera el trabajo") {
    val casos = for (n <- List(1, 7, 64, 1024); u <- List(1, 8, 64)) yield (n, u)
    assert(casos.forall { case (n, u) => profundidad(n, u) <= trabajo(n) })
  }

  //Mis test trabajo

  test("trabajo: cero partículas, cero operaciones") { assert(trabajo(0) == 0) }

  test("trabajo: es lineal (n + m = trabajo(n) + trabajo(m))") {
    assert(trabajo(300) + trabajo(700) == trabajo(1000))
  }

  test("trabajo: es estrictamente creciente") {
    assert(trabajo(10) < trabajo(11) && trabajo(999) < trabajo(1000))
  }

  test("trabajo: calcula correctamente el trabajo para tamaños no potencia de dos") {
    assert(trabajo(37) == 37)
    assert(trabajo(125) == 125)
    assert(trabajo(777) == 777)
  }

  test("trabajo: mantiene el trabajo total al dividirlo en grupos desiguales") {
    val total = trabajo(1000)
    val grupos = trabajo(137) + trabajo(286) + trabajo(577)

    assert(total == grupos)
  }

  test("trabajo: es positivo para n > 0") { assert(trabajo(1) > 0) }

  //Mis tests profundidad
  test("profundidad: n=1, cualquier umbral, es 1") {
    assert(profundidad(1, 1) == 1 && profundidad(1, 100) == 1)
  }

  test("profundidad: con umbral 1, es ⌈log2(n)⌉ + 1") {
    // n=16 -> 4 particiones + 1 = 5 ; n=32 -> 5 + 1 = 6
    assert(profundidad(16, 1) == 5 && profundidad(32, 1) == 6)
  }

  test("profundidad: monótona no decreciente en n (umbral fijo)") {
    assert((1 to 64).forall(n => profundidad(n, 4) <= profundidad(n + 1, 4)))
  }

  test("profundidad: maneja correctamente una división con tamaño impar") {
    assert(profundidad(17, 4) == 6)
  }

  test("profundidad: umbral 0 se comporta como umbral 1 en el límite") {
    // con umbral 0, cualquier n>0 sigue partiendo; el caso base llega a n=0
    // (la recursión n/2 llega a 0). Solo verificamos que termina y es razonable.
    assert(profundidad(8, 0) >= profundidad(8, 1))
  }

  //Mis tests tiempoEstimado

  test("tiempoEstimado: la diferencia entre procesadores corresponde al trabajo ahorrado") {
    val t2 = tiempoEstimado(500, 2, 10)
    val t4 = tiempoEstimado(500, 4, 10)

    assert(cerca(t2 - t4, trabajo(500) / 4.0))
  }

  test("tiempoEstimado: creciente en n (p, umbral fijos)") {
    assert(tiempoEstimado(100, 4, 8) < tiempoEstimado(200, 4, 8))
  }

  test("tiempoEstimado: no creciente en p (n, umbral fijos)") {
    assert(tiempoEstimado(1000, 1, 8) >= tiempoEstimado(1000, 8, 8))
  }

  test("tiempoEstimado: no creciente en el umbral (n, p fijos)") {
    assert(tiempoEstimado(1024, 4, 1) <= tiempoEstimado(1024, 4, 1024))
  }

  test("tiempoEstimado: siempre ≥ profundidad") {
    assert((1 to 20).forall(k =>
      tiempoEstimado(100, k, 4) >= profundidad(100, 4).toDouble))
  }

  // Punto 5 -------------------------------------------------------------------

  test("aceleracion: cuatro veces más rápido da 4.0") {
    assert(cerca(aceleracion(100.0, 25.0), 4.0))
  }

  test("aceleracion: el mismo tiempo da 1.0") {
    assert(cerca(aceleracion(100.0, 100.0), 1.0))
  }

  test("umbralConveniente: sin candidatos devuelve el tamaño del sistema") {
    assert(umbralConveniente(grande, 0.5, Nil) == grande.length)
  }

  test("umbralConveniente: devuelve uno de los candidatos") {
    val candidatos = List(16, 128, 1024)
    assert(candidatos.contains(umbralConveniente(grande, 0.5, candidatos)))
  }

  //Mis tests aceleracion
  test("aceleracion: menor que 1 si el paralelo es más lento") {
    assert(cerca(aceleracion(10.0, 40.0), 0.25))
  }

  test("aceleracion: es recíproca (S(a,b) = 1/S(b,a))") {
    assert(cerca(aceleracion(10.0, 40.0) * aceleracion(40.0, 10.0), 1.0))
  }

  test("aceleracion: escala el numerador") {
    assert(cerca(aceleracion(200.0, 50.0), 2.0 * aceleracion(100.0, 50.0)))
  }

  test("aceleracion: escala el denominador inversamente") {
    assert(cerca(aceleracion(100.0, 100.0), 0.5 * aceleracion(100.0, 50.0)))
  }

  test("aceleracion: no cambia si ambos tiempos se multiplican por el mismo factor") {
    assert(cerca(aceleracion(300.0, 75.0), aceleracion(100.0, 25.0)))
  }

  test("aceleracion: monótona decreciente en tParalelo") {
    assert(aceleracion(100.0, 10.0) > aceleracion(100.0, 20.0))
  }

  //Mis tests umbralConveniente
  test("umbralConveniente: con un único candidato, lo devuelve") {
    assert(umbralConveniente(grande, 0.5, List(37)) == 37)
  }

  test("umbralConveniente: no modifica las partículas del sistema") {
    val sistema = grande.clone()
    val original = sistema.clone()

    umbralConveniente(sistema, 0.5, List(16, 64, 128))

    assert(sistema.indices.forall(i =>
      cerca(sistema(i).pos, original(i).pos) &&
        cerca(sistema(i).vel, original(i).vel) &&
        sistema(i).masa == original(i).masa
    ))
  }

  test("umbralConveniente: conserva la lista de candidatos recibida") {
    val candidatos = List(10, 20, 40, 80)
    val original = candidatos

    umbralConveniente(grande, 0.5, candidatos)

    assert(candidatos == original)
  }

  test("umbralConveniente: funciona con un sistema vacío (lista vacía)") {
    assert(umbralConveniente(Array(), 1.0, Nil) == 0)
  }

  test("umbralConveniente: candidatos repetidos siguen siendo válidos") {
    val cs = List(10, 10, 10)
    assert(umbralConveniente(grande, 0.5, cs) == 10)
  }

  test("umbralConveniente: funciona con candidatos desordenados") {
    val candidatos = List(128, 8, 64, 16)

    val resultado = umbralConveniente(grande, 0.5, candidatos)

    assert(candidatos.contains(resultado))
  }
}
