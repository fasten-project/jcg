import scala.collection.JavaConverters._
import java.io.File
import java.io.FileWriter
import java.util
import java.util.stream.Collectors

import com.ibm.wala.classLoader.Language.JAVA
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl
import com.ibm.wala.ipa.callgraph.AnalysisOptions
import com.ibm.wala.ipa.callgraph.impl.Util
import com.ibm.wala.ipa.cha.ClassHierarchyFactory
import com.ibm.wala.types.MethodReference
import com.ibm.wala.types.TypeReference
import com.ibm.wala.util.NullProgressMonitor
import com.ibm.wala.util.config.AnalysisScopeReader
import play.api.libs.json.Json

import scala.collection.mutable

object WalaJCGAdapter extends JCGTestAdapter {
    override def serializeCG(
        algorithm:  String,
        target:     String,
        mainClass:  String,
        classPath:  Array[String],
        outputFile: String
    ): Long = {
        val before = System.nanoTime
        val cl = Thread.currentThread.getContextClassLoader

        var cp = util.Arrays.stream(classPath).collect(Collectors.joining(File.pathSeparator))
        cp = target + File.pathSeparator + cp

        val ex = new File(cl.getResource("exclusions.txt").getFile)
        val scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(cp, ex)

        val classHierarchy = ClassHierarchyFactory.make(scope)

        val entrypoints =
            if (mainClass == null) {
                new AllSubtypesOfApplicationEntrypoints(scope, classHierarchy)
            } else {
                val mainClassWala = "L"+mainClass.replace(".", "/")
                Util.makeMainEntrypoints(scope, classHierarchy, mainClassWala)
            }

        val options = new AnalysisOptions(scope, entrypoints)
        options.setReflectionOptions(AnalysisOptions.ReflectionOptions.FULL)

        val cache = new AnalysisCacheImpl

        val cg =
            if (algorithm.contains("0-CFA")) {
                val ncfaBuilder = Util.makeZeroCFABuilder(JAVA, options, cache, classHierarchy, scope)
                ncfaBuilder.makeCallGraph(options)
            } else if (algorithm.contains("0-1-CFA")) {
                val cfaBuilder = Util.makeZeroOneCFABuilder(JAVA, options, cache, classHierarchy, scope)
                cfaBuilder.makeCallGraph(options)
            } else if (algorithm.contains("1-CFA")) {
                val cfaBuilder = Util.makeNCFABuilder(1, options, cache, classHierarchy, scope)
                cfaBuilder.makeCallGraph(options)
            } else if (algorithm.contains("RTA")) {
                val rtaBuilder = Util.makeRTABuilder(options, cache, classHierarchy, scope)
                rtaBuilder.makeCallGraph(options, new NullProgressMonitor)
            } else throw new IllegalArgumentException
        val after = System.nanoTime

        val initialEntryPoints = cg.getFakeRootNode.iterateCallSites().asScala.map(_.getDeclaredTarget)

        val worklist = mutable.Queue(initialEntryPoints.toSeq: _*)
        val processed = mutable.Set(worklist: _*)

        var reachableMethods = Set.empty[ReachableMethod]
        while (worklist.nonEmpty) {
            val currentMethod = worklist.dequeue()

            val currentMethodResolved = classHierarchy.resolveMethod(currentMethod)
            if (currentMethodResolved == null) {

            }
            val callSites = for {
                cgNode ← cg.getNodes(currentMethod).asScala
                cs ← cgNode.iterateCallSites().asScala
            } yield {
                val tgtsWala = cg.getPossibleTargets(cgNode, cs).asScala.map(_.getMethod.getReference)
                tgtsWala.foreach { tgt ⇒
                    if (!processed.contains(tgt)) {
                        worklist += tgt
                        processed += tgt
                    }
                }
                if (currentMethodResolved != null) {
                    val declaredTarget = cs.getDeclaredTarget
                    val line = currentMethodResolved.getLineNumber(cs.getProgramCounter)
                    val tgts = tgtsWala.map(createMethodObject).toSet
                    Some(CallSite(createMethodObject(declaredTarget), line, tgts))
                } else {
                    None
                }
            }

            reachableMethods +=
                ReachableMethod(createMethodObject(currentMethod), callSites.toSet.flatten)
        }

        val file: FileWriter = new FileWriter(outputFile)
        file.write(Json.prettyPrint(Json.toJson(ReachableMethods(reachableMethods))))
        file.flush()
        file.close()

        after - before
    }

    override def possibleAlgorithms(): Array[String] = Array("0-1-CFA") //"RTA = "0-CFA = "1-CFA = "0-1-CFA")

    override def frameworkName(): String = "WALA"

    def main(args: Array[String]): Unit = {
        val cgAlgorithm = args(0)
        val targetJar = args(1)
        val mainClass = args(2)
        val outputPath = args(3)
        val cp = args.slice(4, args.length)
        WalaJCGAdapter.serializeCG(cgAlgorithm, targetJar, mainClass, cp, outputPath)
    }

    private def createMethodObject(method: MethodReference): Method = {
        val name = method.getName.toString
        val declaringClass = toJVMString(method.getDeclaringClass)
        val returnType = toJVMString(method.getReturnType)
        val indexes = 0 until method.getNumberOfParameters
        val params = indexes.map(i ⇒ toJVMString(method.getParameterType(i))).toList

        Method(name, declaringClass, returnType, params)

    }

    private def toJVMString(typeReference: TypeReference): String =
        if (typeReference.isClassType ||
            (typeReference.isArrayType && typeReference.getArrayElementType.isClassType)) {
            typeReference.getName.toString+";"
        } else {
            typeReference.getName.toString
        }
}