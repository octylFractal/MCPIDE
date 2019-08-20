package me.kenzierocks.mcpide.fx

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import com.github.javaparser.ast.nodeTypes.NodeWithVariables
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.UnknownType
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import me.kenzierocks.mcpide.exhaustive
import me.kenzierocks.mcpide.fx.SpansScope.Separation.LINE
import me.kenzierocks.mcpide.fx.SpansScope.Separation.NONE
import me.kenzierocks.mcpide.fx.SpansScope.Separation.SPACE
import net.octyl.aptcreator.GenerateCreator
import net.octyl.aptcreator.Provided
import org.fxmisc.richtext.model.StyleSpans
import java.nio.file.Path
import java.util.Optional

class Highlighting(
    val newText: String,
    val spans: StyleSpans<MapStyle>
)

@GenerateCreator
class AstSpans(
    private val directory: Path,
    private val symbolSolver: JavaParserFacade,
    @Provided
    private val spansScopeCreator: SpansScopeCreator,
    @Provided
    private val javaParser: JavaParser
) {
    init {
        javaParser.parserConfiguration.setSymbolResolver(JavaSymbolSolver(symbolSolver.typeSolver))
    }

    fun highlightText(text: String): Highlighting {
        // TODO report errors
        val node = javaParser.parse(text).result.get()
        return highlightNode(node)
    }

    fun highlightNode(node: Node): Highlighting {
        val spans = spansScopeCreator.create(
            directory, symbolSolver
        ).apply {
            add(node)
        }.printer.spans()
        return Highlighting(
            spans.joinToString(separator = "") { it.style.text },
            spans
        )
    }
}

enum class Style(
    val styleClass: String
) {
    DEFAULT_TEXT("default-text"),
    COMMENT("comment"),
    IDENTIFIER("identifier"),
    KEYWORD("keyword"),
    STRING_LITERAL("string-literal"),
    OTHER_LITERAL("other-literal"),
}

@GenerateCreator
class SpansScope(
    private val directory: Path,
    private val symbolSolver: JavaParserFacade,
    @Provided
    private val jumpTargetResolver: JumpTargetResolver
) {

    val printer = SpansPrinter()

    fun add(optional: Optional<out Node>) {
        optional.ifPresent(this::add)
    }

    enum class Separation {
        NONE, SPACE, LINE
    }

    fun addAll(nodes: Collection<Node>?, sep: Separation = NONE) {
        nodes?.forEach {
            add(it)
            when (sep) {
                NONE -> {
                }
                SPACE -> printer.addSpace()
                LINE -> printer.addLine()
            }
        }
    }

    inline fun addCsvSimple(
        collection: Collection<Node>?,
        prefix: SpansPrinter.() -> Unit = {},
        suffix: SpansPrinter.() -> Unit = {}
    ) {
        printer.addCsv(collection, prefix, suffix) { add(it) }
    }

    fun addTypeParamsOrArgs(params: List<Node>?) {
        addCsvSimple(params, prefix = { addText("<") }, suffix = { addText(">") })
    }

    fun addArgs(args: List<Node>?) {
        printer.addText("(")
        if (!args.isNullOrEmpty()) {
            val block: SpansPrinter.() -> Unit = {
                addCsvSimple(args)
            }
            if (args.size > 2) {
                printer.withIndent(block = block)
            } else {
                printer.block()
            }
        }
        printer.addText(")")
    }

    fun addThrows(thrown: List<Node>?) {
        addCsvSimple(thrown,
            prefix = {
                printer.addSpace().addKeyword("throws").addSpace()
            })
    }

    /**
     * Utility for adding a name as a certain style, but dots as default-text.
     *
     * Name must be FULLY QUALIFIED for jump targeting to work. If it's only
     * partially visible, set the FQN as [fqn].
     */
    fun addDotSeparatedName(
        name: Name,
        fqn: String = name.asString(),
        pkg: Boolean = false,
        style: Style = Style.IDENTIFIER) {
        // primary jump-target, can be pkg or type
        val jt = jumpTargetResolver.resolveJumpTarget(
            directory, fqn, nameIsPackage = pkg
        )
        val fqnSplit = fqn.split('.').dropLast(1)
        val nameSplit = name.toString().split('.')
        // number of parts of [fqn] not in [name]
        val diff = (fqnSplit.size + 1) - nameSplit.size
        val jumpTargets = fqnSplit.asSequence().mapIndexed { i, _ ->
            // secondary jump-target, always pkg
            jumpTargetResolver.resolveJumpTarget(
                directory, fqnSplit.subList(0, i + 1).joinToString("."), nameIsPackage = true
            )
        }.drop((diff - 1).coerceAtLeast(0)).toList() + jt
        val parts = nameSplit.zip(jumpTargets)
        parts.forEachIndexed { i, (p, jt) ->
            printer.addSpan(p, jumpTarget = jt, style = style)
            if (i != parts.lastIndex) {
                printer.addText(".")
            }
        }
    }

    /**
     * Sometimes we don't know if something is a type or package.
     *
     * They should never be the same, so this tries both.
     */
    fun resolveAmbigJumpTarget(name: String): JumpTarget? {
        return jumpTargetResolver.resolveJumpTarget(
            directory, name, nameIsPackage = false
        ) ?: jumpTargetResolver.resolveJumpTarget(
            directory, name, nameIsPackage = true
        )
    }

    fun add(node: Node) {
        with(node) {
            exhaustive(when (this) {
                is CompilationUnit -> {
                    add(packageDeclaration)
                    printer.addLine()
                    addAll(imports)
                    printer.addLine()
                    addAll(types, sep = LINE)
                }
                is PackageDeclaration -> {
                    addAll(annotations, sep = LINE)
                    printer.addKeyword("package").addSpace()
                    addDotSeparatedName(name, pkg = true, style = Style.IDENTIFIER)
                    printer.finishLine()
                }
                is ImportDeclaration -> {
                    printer.addKeyword("import").addSpace()
                    if (isStatic) {
                        printer.addKeyword("static").addSpace()
                    }
                    addDotSeparatedName(name)
                    if (isAsterisk) {
                        printer.addText(".*")
                    }
                    printer.finishLine()
                }
                is MarkerAnnotationExpr -> {
                    printer.addKeyword("@")
                    addDotSeparatedName(name, fqn = resolve().qualifiedName)
                }
                is SingleMemberAnnotationExpr -> {
                    printer.addKeyword("@")
                    addDotSeparatedName(name, fqn = resolve().qualifiedName)
                    printer.addText("(")
                    add(memberValue)
                    printer.addText(")")
                }
                is NormalAnnotationExpr -> {
                    printer.addKeyword("@")
                    addDotSeparatedName(name, fqn = resolve().qualifiedName)
                    printer.addText("(")
                    pairs?.let {
                        printer.withIndent {
                            addCsv(pairs) { next ->
                                addText(next.nameAsString, Style.IDENTIFIER)
                                addText("=")
                                add(next.value)
                            }
                        }
                    }
                    printer.addText(")")
                }
                is ArrayAccessExpr -> {
                    add(name)
                    printer.addText("[")
                    add(index)
                    printer.addText("]")
                }
                is ArrayCreationExpr -> {
                    printer.addKeyword("new").addSpace()
                    add(elementType)
                    addAll(levels)
                    initializer.ifPresent {
                        printer.addSpace()
                        add(it)
                    }
                }
                is ArrayCreationLevel -> {
                    addAll(annotations, sep = SPACE)
                    printer.addText("[")
                    add(dimension)
                    printer.addText("]")
                }
                is ArrayInitializerExpr -> {
                    printer.addText("{")
                    addCsvSimple(values, prefix = { addSpace() })
                    printer.addText("}")
                }
                is ClassOrInterfaceDeclaration -> {
                    addAll(annotations, sep = LINE)
                    printer.addModifiers(modifiers)
                    printer.addKeyword(when {
                        isInterface -> "interface"
                        else -> "class"
                    }).addSpace()
                    printer.addIdentifier(nameAsString)

                    addTypeParamsOrArgs(typeParameters)

                    addCsvSimple(extendedTypes,
                        prefix = { addSpace().addKeyword("extends").addSpace() })
                    addCsvSimple(implementedTypes,
                        prefix = { addSpace().addKeyword("implements").addSpace() })
                    printer.addText(" ").addBlock {
                        addAll(members, sep = LINE)
                    }
                }
                is ClassOrInterfaceType -> {
                    scope.ifPresent {
                        add(it)
                        printer.addText(".")
                    }
                    addAll(annotations, sep = SPACE)
                    val jt = resolveAmbigJumpTarget(resolve().qualifiedName)
                    printer.addIdentifier(nameAsString, jt)

                    if (isUsingDiamondOperator) {
                        printer.addText("<>")
                    } else {
                        addTypeParamsOrArgs(typeArguments.orElse(null))
                    }
                }
                is FieldDeclaration -> {
                    addAll(annotations, sep = LINE)
                    printer.addModifiers(modifiers)
                    if (variables.isNonEmpty) {
                        maximumCommonType.ifPresentOrElse(this@SpansScope::add) {
                            // I do wonder if I should throw here. PrettyPrintVisitor doesn't.
                            printer.addText("???")
                        }
                    }

                    printer.addSpace()
                    addCsvSimple(variables)
                    printer.addText(";")
                }
                is VariableDeclarator -> {
                    printer.addIdentifier(nameAsString)

                    findAncestor(NodeWithVariables::class.java).flatMap { it.maximumCommonType }.ifPresent { mct ->
                        var arrayType: ArrayType? = null
                        for (i in mct.arrayLevel until type.arrayLevel) {
                            arrayType = when (arrayType) {
                                null -> type
                                else -> arrayType.componentType
                            } as ArrayType
                            addAll(arrayType.annotations, sep = SPACE)
                            printer.addText("[]")
                        }
                    }

                    initializer.ifPresent {
                        printer.addText(" = ")
                        add(it)
                    }
                }
                is MethodCallExpr -> {
                    scope.ifPresent {
                        add(it)
                        printer.addText(".")
                    }

                    addTypeParamsOrArgs(typeArguments.orElse(null))
                    printer.addIdentifier(nameAsString)
                    addArgs(arguments)
                }
                is NameExpr -> {
                    printer.addIdentifier(nameAsString)
                }
                is ConstructorDeclaration -> {
                    addAll(annotations, sep = LINE)
                    printer.addModifiers(modifiers)

                    addTypeParamsOrArgs(typeParameters)
                    if (isGeneric) {
                        printer.addSpace()
                    }
                    printer.addIdentifier(nameAsString)

                    addArgs(parameters)
                    addThrows(thrownExceptions)

                    printer.addSpace()
                    add(body)
                }
                is Parameter -> {
                    addAll(annotations, sep = SPACE)
                    printer.addModifiers(modifiers)

                    add(type)
                    if (isVarArgs) {
                        addAll(varArgsAnnotations, sep = SPACE)
                        printer.addText("...")
                    }
                    if (type !is UnknownType) {
                        printer.addSpace()
                    }
                    printer.addIdentifier(nameAsString)
                }
                is ArrayType -> {
                    val (arrayTypes, innerType) = generateSequence<Type>(this) {
                        prev -> (prev as? ArrayType)?.componentType
                    }.toList().partition { it is ArrayType }

                    add(innerType.single())
                    arrayTypes.forEach {
                        addAll(it.annotations, sep = SPACE)
                        printer.addText("[]")
                    }
                }
                is BlockStmt -> {
                    printer.addBlock {
                        addAll(statements, sep = LINE)
                    }
                }
                is ExplicitConstructorInvocationStmt -> {
                    if (isThis) {
                        addTypeParamsOrArgs(typeArguments.orElse(null))
                        printer.addKeyword("this")
                    } else {
                        expression.ifPresent {
                            add(it)
                            printer.addText(".")
                        }
                        addTypeParamsOrArgs(typeArguments.orElse(null))
                        printer.addKeyword("super")
                    }
                    addArgs(arguments)
                    printer.addText(";")
                }
                is MethodDeclaration -> {
                    addAll(annotations, sep = LINE)
                    printer.addModifiers(modifiers)
                    addTypeParamsOrArgs(typeParameters)
                    if (!typeParameters.isNullOrEmpty()) {
                        printer.addSpace()
                    }

                    add(type)
                    printer.addSpace()
                    printer.addIdentifier(nameAsString)
                }
                else ->
                    throw IllegalStateException("Unhandled Node type: ${this.javaClass.name}")
            })
        }
    }

}
