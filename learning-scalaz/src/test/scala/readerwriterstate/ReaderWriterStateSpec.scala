package readerwriterstate

import org.scalatest.{Matchers, FunSuite}
import scalaz._, Scalaz._

import scala.concurrent._

class ReaderWriterStateSpec extends FunSuite with Matchers {
  import Database._

  case class Person(name: String, address: Address)
  case class Address(street: String)

  def sleep(millis: Long) = java.lang.Thread.sleep(millis)

  def getPerson(name: String): Task[Person] = createTask { conn =>
    val rs = conn.executeAndReturn(s"SELECT * FROM USER WHERE name == '$name'")

    /* do something with result set */
    sleep(50)

    Person(name, Address("BACON STREET 134"))
  }

  def updateAddress(person : Person): Task[Unit] = createTask { conn =>

    /* do something using person before updating database */
    sleep(50)

    conn.execute(
      s"UPDATE ADDRESS SET street = '${person.address.street}' where person_name = '${person.name}'")
  }

  test("Database example1") {

    val getAndUpdatePersonTask: Task[Person] = for {
      p <- getPerson("1ambda")
      updatedP = p.copy(address = Address("BACON STREET 234"))
      _ <- addPostCommitAction(() => println("post commit action1"))
      _ <- updateAddress(updatedP)
      _ <- addPostCommitAction(() => println("post commit action2"))
    } yield updatedP


    import Database.Implicit._
    val person: Option[Person] = Database.run(getAndUpdatePersonTask)

    person match {
      case Some(person) =>
        person.name shouldBe "1ambda"
        person.address.street shouldBe "BACON STREET 234"

      case None => fail()
    }
  }

  test("Database example2") {

    val slowQuery: Task[Person] = createTask { conn =>
      sleep(600)
      Person("Sherlock", Address("BACON ST 221-B"))
    }

    val getPeopleTask: Task[List[Person]] = for {
      p1 <- getPerson("Mycroft")
      p2 <- getPerson("Watson")
      p3 <- slowQuery
      _ <- addPostCommitAction(() => println("got 3 people"))
    } yield p1 :: p2 :: p3 :: Nil

    import Database.Implicit._
    val people = Database.run(getPeopleTask)

    // log: Operation failed due to java.lang.RuntimeException: Operation timeout: 603 millis
    people shouldBe None
  }

  /**
   * IO ST
   * http://underscore.io/blog/posts/2015/04/28/monadic-io-laziness-makes-you-free.html
   * http://stackoverflow.com/questions/19687470/scala-io-monad-whats-the-point
   * https://apocalisp.wordpress.com/2011/03/20/towards-an-effect-system-in-scala-part-1/
   * https://apocalisp.wordpress.com/2011/12/19/towards-an-effect-system-in-scala-part-2-io-monad/
   *
   * https://github.com/fpinscala/fpinscala/wiki/Chapter-14:-Local-effects-and-mutable-state
   * https://github.com/fpinscala/fpinscala/wiki/Chapter-13:-External-effects-and-IO
   * https://github.com/fpinscala/fpinscala/wiki/Chapter-15:-Stream-processing-and-incremental-IO
   *
   * FREE, TRAMPOLINE
   * https://apocalisp.wordpress.com/2011/10/26/tail-call-elimination-in-scala-monads/
   * http://blog.higher-order.com/blog/2015/06/18/easy-performance-wins-with-scalaz/
   *
   * ADVANCED MONAD
   * https://github.com/fpinscala/fpinscala/wiki/Chapter-11:-Monads
   */
}
