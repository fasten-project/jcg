import java.io.File
import java.io.FileInputStream

import lib.annotations.documentation.CGFeature
import org.opalj.br
import org.opalj.br.Annotation
import org.opalj.br.AnnotationValue
import org.opalj.br.ArrayValue
import org.opalj.br.BooleanValue
import org.opalj.br.ClassValue
import org.opalj.br.ElementValuePair
import org.opalj.br.EnumValue
import org.opalj.br.IntValue
import org.opalj.br.ObjectType
import org.opalj.br.StringValue
import org.opalj.br.Type
import org.opalj.br.VoidType
import org.opalj.br.analyses.Project
import org.opalj.log.GlobalLogContext
import org.opalj.log.LogContext
import org.opalj.log.LogMessage
import org.opalj.log.OPALLogger
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.api.libs.json.Reads

case class CallSites(callSites: Set[CallSite])

case class CallSite(declaredTarget: Method, line: Int, method: Method, targets: Set[Method])

case class Method(name: String, declaringClass: String, returnType: String, parameterTypes: List[String])

class DevNullLogger extends OPALLogger {
    override def log(message: LogMessage)(implicit ctx: LogContext): Unit = {}
}

object CGMatcher {

    val callSiteAnnotationType = ObjectType("lib/annotations/callgraph/CallSite")
    val callSitesAnnotationType = ObjectType("lib/annotations/callgraph/CallSites")
    val indirectCallAnnotationType = ObjectType("lib/annotations/callgraph/IndirectCall")
    val indirectCallsAnnotationType = ObjectType("lib/annotations/callgraph/IndirectCalls")

    def matchCallSites(tgtJar: String, jsonPath: String, verbose: Boolean = false): Boolean = {
        OPALLogger.updateLogger(GlobalLogContext, new DevNullLogger())
        val p = Project(new File(tgtJar))

        val json = Json.parse(new FileInputStream(new File(jsonPath)))
        implicit val methodReads: Reads[Method] = Json.reads[Method]
        implicit val callSiteReads: Reads[CallSite] = Json.reads[CallSite]
        implicit val callSitesReads: Reads[CallSites] = Json.reads[CallSites]
        val jsResult = json.validate[CallSites]
        jsResult match {
            case _: JsSuccess[CallSites] ⇒
                val computedCallSites = jsResult.get
                for (clazz ← p.allProjectClassFiles) {
                    for ((method, _) ← clazz.methodsWithBody) {
                        // check if the call site might not be ambiguous
                        if (method.annotations.exists { a ⇒
                            a.annotationType == callSiteAnnotationType ||
                                a.annotationType == callSitesAnnotationType ||
                                a.annotationType == indirectCallAnnotationType ||
                                a.annotationType == indirectCallsAnnotationType
                        }) {
                            val body = method.body.get
                            val invokations = body.instructions.filter(instr ⇒ instr != null && instr.isInvocationInstruction)
                            val lines = invokations.zipWithIndex.map {
                                case (instr, pc) ⇒
                                    (body.lineNumber(pc), instr.asInvocationInstruction.name)
                            }.toSet
                            if (lines.size != invokations.length) {
                                throw new RuntimeException(s"Multiple call sites with same name in the same line $tgtJar, ${method.name}")
                            }
                        }

                        for (annotation ← method.annotations) {

                            val callSiteAnnotations =
                                if (annotation.annotationType == callSiteAnnotationType)
                                    Seq(annotation)
                                else if (annotation.annotationType == callSitesAnnotationType)
                                    getAnnotations(annotation, "value")
                                else
                                    Seq.empty

                            if (!handleCallSiteAnnotations(
                                computedCallSites.callSites,
                                method,
                                callSiteAnnotations,
                                verbose
                            ))
                                return false;

                            val indirectCallAnnotations =
                                if (annotation.annotationType == indirectCallAnnotationType)
                                    Seq(annotation)
                                else if (annotation.annotationType == indirectCallsAnnotationType)
                                    getAnnotations(annotation, "value")
                                else
                                    Seq.empty

                            if (!handleIndirectCallAnnotations(
                                computedCallSites.callSites,
                                method,
                                indirectCallAnnotations,
                                verbose
                            ))
                                return false;
                        }
                    }
                }

                true
            case _ ⇒
                throw new RuntimeException("Unable to parse json")
        }
    }

    private def verifyCallSite(annotatedLineNumber: Int, src: br.Method, tgtName: String): Unit = {
        val body = src.body.get
        val existsInstruction = body.instructions.zipWithIndex.exists {
            case (instr, pc) if instr.isInvocationInstruction ⇒
                val lineNumber = body.lineNumber(pc)
                // todo parameter types
                lineNumber.isDefined && annotatedLineNumber == lineNumber.get && tgtName == instr.asInvocationInstruction.name
        }
        if (!existsInstruction)
            throw new RuntimeException(s"There is no call to $tgtName in line $annotatedLineNumber")
    }

    private def handleCallSiteAnnotations(
        computedCallSites:   Set[CallSite],
        method:              br.Method,
        callSiteAnnotations: Seq[Annotation],
        verbose:             Boolean
    ): Boolean = {
        for (callSiteAnnotation ← callSiteAnnotations) {
            val line = getLineNumber(callSiteAnnotation)
            val name = getString(callSiteAnnotation, "name")
            val returnType = getType(callSiteAnnotation, "returnType")
            val parameterTypes = getParameterList(callSiteAnnotation)
            verifyCallSite(line, method, name)
            val feature = getFeatureEnum(callSiteAnnotation)
            val annotatedMethod = convertMethod(method)

            val annotatedTargets =
                getAnnotations(callSiteAnnotation, "resolvedMethods").map(getString(_, "receiverType"))

            computedCallSites.find { cs ⇒
                cs.line == line && cs.method == annotatedMethod && cs.declaredTarget.name == name
            } match {
                case Some(computedCallSite) ⇒

                    val computedTargets = computedCallSite.targets.map(_.declaringClass)

                    for (annotatedTgt ← annotatedTargets) {
                        if (!computedTargets.contains(annotatedTgt)) {
                            if (verbose) println(s"$line:${annotatedMethod.declaringClass}#${annotatedMethod.name}:\t there is no call to $annotatedTgt#$name")
                                return false;
                        } else {
                            if (verbose) println("found it")
                        }
                    }

                    val prohibitedTargets =
                        getAnnotations(callSiteAnnotation, "prohibitedMethods").map(getString(_, "receiverType"))
                    for (prohibitedTgt ← prohibitedTargets) {
                        if (computedTargets.contains(prohibitedTgt)) {
                            if (verbose) println(s"$line:${annotatedMethod.declaringClass}#${annotatedMethod.name}:\t there is a call to prohibited target $prohibitedTgt#$name")
                            return false;
                        } else {
                            if (verbose) println("no call to prohibited")
                        }
                    }
                case _ ⇒
                    throw new RuntimeException(s"$line:${annotatedMethod.declaringClass}#${annotatedMethod.name}:\t there is no callsite to method $name")
            }
        }

        true
    }

    private def verifyCallExistance(annotatedLineNumber: Int, method: br.Method): Unit = {
        val body = method.body.get
        body.instructions.zipWithIndex.exists {
            case (instr, pc) ⇒
                val lineNumber = body.lineNumber(pc)
                instr != null && instr.isInvocationInstruction && lineNumber.isDefined && lineNumber.get == annotatedLineNumber
        }
    }

    private def handleIndirectCallAnnotations(
        computedCallSites:       Set[CallSite],
        source:                  br.Method,
        indirectCallAnnotations: Seq[Annotation],
        verbose:                 Boolean
    ): Boolean = {
        for (annotation ← indirectCallAnnotations) {
            val line = getLineNumber(annotation)
            verifyCallExistance(line, source)
            val name = getString(annotation, "name")
            val returnType = getReturnType(annotation).toJVMTypeName
            val parameterTypes = getParameterList(annotation).map(_.toJVMTypeName)
            val declaringClass = getString(annotation, "declaringClass")
            val annotatedTarget = Method(name, declaringClass, returnType, parameterTypes)
            val annotatedSource = convertMethod(source)
            val feature = getFeatureEnum(annotation)
            if (!callsIndirectly(computedCallSites, annotatedSource, annotatedTarget, verbose))
                return false;
        }
        true
    }

    private def callsIndirectly(
        computedCallSites: Set[CallSite],
        source:            Method,
        annotatedTarget:   Method,
        verbose:           Boolean
    ): Boolean = {
        var visited: Set[Method] = Set(source)
        var workset: Set[Method] = Set(source)

        while (workset.nonEmpty) {
            val currentSource = workset.head
            workset = workset.tail

            for (tgt ← computedCallSites.filter(_.method == currentSource).flatMap(_.targets)) {
                if (tgt == annotatedTarget) {
                    if (verbose) println(s"Found transitive call $source -> $annotatedTarget")
                    return true;
                }

                if (!visited.contains(tgt)) {
                    visited += tgt
                    workset += tgt
                }
            }
        }

        if (verbose) println(s"Missed transitive call $source -> $annotatedTarget")

        false
    }

    def main(args: Array[String]): Unit = {
        matchCallSites(args(0), args(1), verbose = true)
    }

    def convertMethod(method: org.opalj.br.Method): Method = {
        val name = method.name
        val declaringClass = method.classFile.thisType.toJVMTypeName
        val returnType = method.returnType.toJVMTypeName
        val parameterTypes = method.parameterTypes.map(_.toJVMTypeName).toList

        Method(name, declaringClass, returnType, parameterTypes)
    }

    //
    // UTILITY FUNCTIONS
    //
    def getAnnotations(callSites: Annotation, label: String): Seq[Annotation] = { //@CallSites -> @CallSite[]
        val avs = callSites.elementValuePairs collectFirst {
            case ElementValuePair(`label`, ArrayValue(array)) ⇒ array
        }
        avs.getOrElse(IndexedSeq.empty).map { cs ⇒ cs.asInstanceOf[AnnotationValue].annotation }
    }

    def getString(callSite: Annotation, label: String): String = { //@CallSite -> String
        val sv = callSite.elementValuePairs collectFirst {
            case ElementValuePair(`label`, StringValue(string)) ⇒ string
        }
        sv.getOrElse("")
    }

    def getLineNumber(callSite: Annotation): Int = { //@CallSite -> int
        val iv = callSite.elementValuePairs collectFirst {
            case ElementValuePair("line", IntValue(int)) ⇒ int
        }
        iv.getOrElse(-1)
    }

    def getBoolean(callSite: Annotation, label: String): Boolean = { //@CallSite -> boolean
        val bv = callSite.elementValuePairs collectFirst {
            case ElementValuePair(`label`, BooleanValue(bool)) ⇒ bool
        }
        bv.getOrElse(false)
    }

    def getType(annotation: Annotation, label: String): Type = { //@CallSite -> Type
        val cv = annotation.elementValuePairs collectFirst {
            case ElementValuePair(`label`, ClassValue(declaringType)) ⇒ declaringType
        }
        cv.getOrElse(VoidType)
    }

    def getReturnType(annotation: Annotation): Type = { //@CallSite -> Type
        getType(annotation, "returnType")
    }

    def getParameterList(callSite: Annotation): List[Type] = { //@CallSite -> Seq[FieldType]
        val av = callSite.elementValuePairs collectFirst {
            case ElementValuePair("parameterTypes", ArrayValue(ab)) ⇒
                ab.toIndexedSeq.map(ev ⇒
                    ev.asInstanceOf[ClassValue].value)
        }
        av.getOrElse(List()).toList
    }

    def getFeatureEnum(callSite: Annotation): CGFeature = {
        val feature = callSite.elementValuePairs collectFirst {
            case ElementValuePair("feature", EnumValue(_, constName)) ⇒
                constName
        }
        CGFeature.valueOf(feature.getOrElse("Misc"))
    }
}