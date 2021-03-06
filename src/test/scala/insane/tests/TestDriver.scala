package phantm.tests

import java.io._

import scala.xml._

import phantm.util._

import phantm.phases._
import phantm._

import phantm.ast.Trees.Program
import phantm.ast.STToAST

import scala.io.Source

import org.scalatest.FunSuite
import org.scalatest.matchers._

class TestResult(var errors: Set[String] = Set()) {
  def isSuccess = errors.isEmpty
  def containsError(str: String) = errors.exists(_ contains str)
  def containsOnlyError(str: String) = errors.forall(_ contains str)
}

class CrashedResult(e: Throwable) extends TestResult(Set(e.getMessage))

trait PhantmTestDriver extends FunSuite with MustMatchers {

  class IsTestSuccessful extends BeMatcher[TestResult] {
    def apply(left: TestResult) = {
      MatchResult(left.isSuccess, "Errors found: "+left.errors.mkString(", "), "No error found")
    }
  }

  case class ContainsError(msg: String) extends BeMatcher[TestResult] {
    def apply(left: TestResult) = {
      MatchResult(left.containsError(msg), 
        "Errors mismatch: "+left.errors.mkString(", ")+" (was looking for "+msg+")",
        "Notice "+msg+" detected!")
    }
  }


  val successful = new IsTestSuccessful
  val failing  = new IsTestSuccessful

  import java.io.{File, BufferedWriter, FileWriter}

  def tmpFileWithContent(content: String): File = {
    val tmpFile = File.createTempFile("phantmtest", ".php");
    tmpFile.deleteOnExit();

    val out = new BufferedWriter(new FileWriter(tmpFile));
    out.write(content);
    out.close();

    tmpFile
  }

  def testFile(settings: Settings, file: File): TestResult = {
    var tr = new TestResult

    class TestReporter(files: List[String]) extends Reporter(files) {
      override def notice(e: String): Boolean = {
        tr.errors += e
        true
      }
      override def error(e: String): Boolean = {
        tr.errors += e
        true
      }
      override def addError(e: Error): Boolean = {
        tr.errors += e.message +" @ "+e.pos.file.getOrElse("?")+":"+e.pos.line
        true
      }

      override def emitSummary = {

      }
    }


    try {
      val files = List(file.getAbsolutePath)
      val rep = new TestReporter(files)
      Reporter.set(rep)
      Settings.set(settings)
      new PhasesRunner(rep).run(new PhasesContext(files = files))
      tr
    } catch {
      case e: Throwable => new CrashedResult(e)
    }
  }


  def testFilePath(settings: Settings, path: String): TestResult = {
    testFile(settings, new File(path))
  }
  def testString(settings: Settings, str: String): TestResult = {
    testFile(settings, tmpFileWithContent(str))
  }

  def findTests(in: String, pattern: String): List[File] = {
    findTests(new File(in), pattern)
  }
  def findTests(in: File, pattern: String): List[File] = {
    in.listFiles.filter(_.getName().startsWith(pattern)).toList.sorted
  }

  def testAndExpect(path: String, error: String, settings: Settings = Settings(verbosity = 2)) {
    test(path) {
      val tr = testFilePath(settings, path)
      tr must be (ContainsError(error))
    }
  }

  def testPass(path: String, settings: Settings = Settings(verbosity = 2)) {
    test(path) {
      val tr = testFilePath(settings, path)
      tr must be (successful)
    }
  }

  def testFail(path: String, settings: Settings = Settings(verbosity = 2)) {
    var settings = Settings(verbosity = 2)
    test(path) {
      val tr = testFilePath(settings, path)
      tr must not be (successful)
    }
  }
}
