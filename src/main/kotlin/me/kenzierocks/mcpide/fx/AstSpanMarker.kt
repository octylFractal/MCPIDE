/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.kenzierocks.mcpide.fx

import com.github.javaparser.GeneratedJavaParserConstants
import com.github.javaparser.JavaParser
import com.github.javaparser.JavaToken
import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.ConditionalExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.EnclosedExpr
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.InstanceOfExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LambdaExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.MethodReferenceExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.SuperExpr
import com.github.javaparser.ast.expr.SwitchExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.nodeTypes.NodeWithVariables
import com.github.javaparser.ast.nodeTypes.SwitchNode
import com.github.javaparser.ast.stmt.AssertStmt
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.BreakStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.ContinueStmt
import com.github.javaparser.ast.stmt.EmptyStmt
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.stmt.ForStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.LabeledStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.stmt.SwitchStmt
import com.github.javaparser.ast.stmt.SynchronizedStmt
import com.github.javaparser.ast.stmt.ThrowStmt
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.stmt.WhileStmt
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.TypeParameter
import com.github.javaparser.ast.type.UnionType
import com.github.javaparser.ast.type.UnknownType
import com.github.javaparser.ast.type.VoidType
import com.github.javaparser.ast.type.WildcardType
import me.kenzierocks.mcpide.inject.ProjectScope
import me.kenzierocks.mcpide.util.findTokenFor
import me.kenzierocks.mcpide.util.tokens
import me.kenzierocks.mcpide.util.typesolve.NodeTypeFinder
import net.octyl.aptcreator.GenerateCreator
import net.octyl.aptcreator.Provided
import java.nio.file.Path
import java.util.Optional

@[GenerateCreator GenerateCreator.CopyAnnotations]
@ProjectScope
class AstSpanMarker(
    private val root: Path,
    private val jeaDoc: JeaDoc,
    @Provided
    private val javaParser: JavaParser,
    @Provided
    private val jumpTargetResolver: JumpTargetResolver,
    @Provided
    private val nodeTypeFinder: NodeTypeFinder
) {
    fun markAst() {
        // TODO report errors
        val node = javaParser.parse(jeaDoc.text).result.get()
        markNode(node)
    }

    private fun markToken(
        token: JavaToken, style: Style, jumpTarget: JumpTarget? = null
    ) {
        markRange(jeaDoc.offsets(token), style, jumpTarget)
    }

    private fun markRange(
        range: IntRange, style: Style, jumpTarget: JumpTarget? = null
    ) {
        val originalStyle = jeaDoc.getStyleSpans(range.first, range.last).single().style
        jeaDoc.setStyle(range.first, range.last, originalStyle.copy(
            styleClasses = setOf(style.styleClass), jumpTarget = jumpTarget
        ))
    }

    /**
     * Find a keyword in the given node, and mark it.
     */
    private fun Node.markKeyword(tokenText: String) {
        val token = findTokenFor(tokenText)
        markToken(token, Style.KEYWORD)
    }

    private fun markDotSeparatedName(
        name: Name,
        fqn: String? = null,
        pkg: Boolean = false
    ) {
        val fqnOrName = fqn ?: name.asString()
        // primary jump-target, can be pkg or type
        val jt = jumpTargetResolver.resolveJumpTarget(
            root, fqnOrName, nameIsPackage = pkg
        )
        val fqnSplit = fqnOrName.split('.').dropLast(1)
        val nameSplit = name.tokens.filter { it.kind == GeneratedJavaParserConstants.IDENTIFIER }
        // number of parts of [fqn] not in [name]
        val diff = (fqnSplit.size + 1) - nameSplit.size
        val jumpTargets = fqnSplit.asSequence().mapIndexed { i, _ ->
            // secondary jump-target, always pkg
            jumpTargetResolver.resolveJumpTarget(
                root, fqnSplit.subList(0, i + 1).joinToString("."), nameIsPackage = true
            )
        }.drop((diff - 1).coerceAtLeast(0)).toList() + jt
        nameSplit.zip(jumpTargets).forEach { (p, jt) ->
            markToken(p, Style.IDENTIFIER, jt)
        }
    }

    private fun markSimpleName(name: SimpleName, fqn: String? = null) {
        val jt = fqn?.let {
            jumpTargetResolver.resolveJumpTarget(root, fqn, nameIsPackage = false)
        }
        markToken(name.tokens.single(), Style.IDENTIFIER, jt)
    }

    private fun markNode(node: Optional<out Node>) {
        node.ifPresent { markNode(it) }
    }

    private fun markNodes(nodes: List<Node>?) {
        nodes?.forEach { markNode(it) }
    }

    private fun markNodes(nodes: Optional<out List<Node>>) = markNodes(nodes.orElse(null))

    private fun markNode(node: Node?) {
        node ?: return
        val marker = requireNotNull(nodeMarkTable[node.javaClass]) { "No marker for ${node.javaClass.name}" }
        node.marker()
    }

    private val nodeMarkTable: NodeMarkTable = NodeMarkTable().apply {
        // Note: There's no real ordering to the following statements.
        // I just ran [AstSpanMarkerFullTest] over every file in the Minecraft JAR
        // And added something every time it errored out.
        // This may or may not be complete.
        add<CompilationUnit> {
            check(parsed == Node.Parsedness.PARSED) { "CompilationUnit isn't parsed!" }
            markNode(packageDeclaration)
            markNodes(imports)
            markNodes(types)
            markNode(module)
        }
        add<PackageDeclaration> {
            markNodes(annotations)
            markKeyword("package")
            markDotSeparatedName(name, pkg = true)
        }
        add<MarkerAnnotationExpr> {
            markKeyword("@")
            markDotSeparatedName(name, fqn = nodeTypeFinder.requireType(nameAsString, this))
        }
        add<ImportDeclaration> {
            markKeyword("import")
            if (isStatic) {
                markKeyword("static")
            }
            markDotSeparatedName(name)
        }
        add<ClassOrInterfaceDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)
            markKeyword(when {
                isInterface -> "interface"
                else -> "class"
            })
            markSimpleName(name)
            markNodes(typeParameters)
            if (extendedTypes.isNonEmpty) {
                markKeyword("extends")
                markNodes(extendedTypes)
            }
            if (implementedTypes.isNonEmpty) {
                markKeyword("implements")
                markNodes(implementedTypes)
            }
            markNodes(members)
        }
        add<Modifier> {
            markKeyword(keyword.asString())
        }
        add<ClassOrInterfaceType> {
            markNode(scope)
            markNodes(annotations)
            markSimpleName(name, fqn = nodeTypeFinder.requireTypeUnless(nameAsString, this) { name ->
                // Mis-classified variables
                name[0].isLowerCase() || name.all { it.isUpperCase() || it == '_' }
            })
            markNodes(typeArguments)
        }
        add<FieldDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)
            if (variables.isNonEmpty) {
                markNode(maximumCommonType)
            }
            markNodes(variables)
        }
        add<VariableDeclarator> {
            markSimpleName(name)
            findAncestor(NodeWithVariables::class.java)
                .flatMap { it.maximumCommonType }
                .filter { it.arrayLevel < type.arrayLevel }
                .ifPresent { commonType ->
                    generateSequence(type as ArrayType) { it.componentType as? ArrayType }
                        .take(type.arrayLevel - commonType.arrayLevel)
                        .forEach { markNodes(it.annotations) }
                }
            markNode(initializer)
        }
        add<MethodCallExpr> {
            markNode(scope)
            markNodes(typeArguments)
            markSimpleName(name)
            markNodes(arguments)
        }
        add<NameExpr> {
            markSimpleName(name)
        }
        add<ConstructorDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)
            markNodes(typeParameters)
            markSimpleName(name)
            markNodes(parameters)
            if (!thrownExceptions.isNullOrEmpty()) {
                markKeyword("throws")
                markNodes(thrownExceptions)
            }
            markNode(body)
        }
        add<Parameter> {
            markNodes(annotations)
            markNodes(modifiers)
            markNode(type)
            if (isVarArgs) {
                markNodes(varArgsAnnotations)
            }
            markSimpleName(name)
        }
        add<ArrayType> {
            val (aTypes, other) = generateSequence<Type>(this) { (it as? ArrayType)?.componentType }
                .partition { it is ArrayType }
            markNode(other.single())
            aTypes.forEach { markNodes(it.annotations) }
        }
        add<BlockStmt> {
            markNodes(statements)
        }
        add<ExplicitConstructorInvocationStmt> {
            when {
                isThis -> markKeyword("this")
                else -> {
                    markNode(expression)
                    markNodes(typeArguments)
                    markKeyword("super")
                }
            }
            markNodes(arguments)
        }
        add<MethodDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)
            markNodes(typeParameters)
            markNode(type)
            markSimpleName(name)
            markNode(receiverParameter)
            markNodes(parameters)
            if (!thrownExceptions.isNullOrEmpty()) {
                markKeyword("throws")
                markNodes(thrownExceptions)
            }
            markNode(body)
        }
        add<IfStmt> {
            markKeyword("if")
            markNode(condition)
            markNode(thenStmt)
            markNode(elseStmt)
        }
        add<ReturnStmt> {
            markKeyword("return")
            markNode(expression)
        }
        add<ExpressionStmt> {
            markNode(expression)
        }
        add<VariableDeclarationExpr> {
            markNodes(annotations)
            markNodes(modifiers)
            if (variables.isNonEmpty) {
                markNode(maximumCommonType)
            }
            markNodes(variables)
        }
        add<FieldAccessExpr> {
            markNode(scope)
            markSimpleName(name)
        }
        add<ObjectCreationExpr> {
            markNode(scope)
            markKeyword("new")
            markNodes(typeArguments)
            markNode(type)
            markNodes(arguments)
            markNodes(anonymousClassBody)
        }
        add<UnaryExpr> {
            // Skipped operators here, since there's no highlighting.
            markNode(expression)
        }
        add<StringLiteralExpr> {
            tokens.forEach { markToken(it, Style.STRING_LITERAL) }
        }
        add<CastExpr> {
            markNode(type)
            markNode(expression)
        }
        add<WildcardType> {
            markNodes(annotations)
            extendedType.ifPresent {
                markKeyword("extends")
                markNode(it)
            }
            superType.ifPresent {
                markKeyword("super")
                markNode(it)
            }
        }
        add<MethodReferenceExpr> {
            markNode(scope)
            markNodes(typeArguments)
            when (identifier) {
                "new" -> markKeyword("new")
                else -> markToken(findTokenFor(identifier), Style.IDENTIFIER)
            }
        }
        add<TypeExpr> {
            markNode(type)
        }
        add<ClassExpr> {
            markNode(type)
            markKeyword("class")
        }
        add<AssignExpr> {
            markNode(target)
            markNode(value)
        }
        add<ThisExpr> {
            typeName.ifPresent {
                markDotSeparatedName(it, fqn = nodeTypeFinder.requireType(it.asString(), this))
            }
            markKeyword("this")
        }
        add<BinaryExpr> {
            markNode(left)
            markNode(right)
        }
        add<PrimitiveType> {
            markNodes(annotations)
            markKeyword(type.asString())
        }
        add<IntegerLiteralExpr> {
            markToken(findTokenFor(value), Style.OTHER_LITERAL)
        }
        add<VoidType> {
            markNodes(annotations)
            markKeyword("void")
        }
        add<SuperExpr> {
            typeName.ifPresent {
                markDotSeparatedName(it, fqn = nodeTypeFinder.requireType(it.asString(), this))
            }
            markKeyword("super")
        }
        add<ForEachStmt> {
            markKeyword("for")
            markNode(variable)
            markNode(iterable)
            markNode(body)
        }
        add<NullLiteralExpr> {
            markKeyword("null")
        }
        add<ThrowStmt> {
            markKeyword("throw")
            markNode(expression)
        }
        add<LambdaExpr> {
            markNodes(parameters)
            markNode(when (val b = body) {
                is ExpressionStmt -> b.expression
                else -> b
            })
        }
        add<ConditionalExpr> {
            markNode(condition)
            markNode(thenExpr)
            markNode(elseExpr)
        }
        add<UnknownType>(NO_OP)
        add<TryStmt> {
            markKeyword("try")
            markNodes(resources)
            markNode(tryBlock)
            markNodes(catchClauses)
            finallyBlock.ifPresent {
                markKeyword("finally")
                markNode(it)
            }
        }
        add<CatchClause> {
            markKeyword("catch")
            markNode(parameter)
            markNode(body)
        }
        add<BooleanLiteralExpr> {
            markKeyword(value.toString())
        }
        add<LongLiteralExpr> {
            markToken(findTokenFor(value), Style.OTHER_LITERAL)
        }
        add<DoubleLiteralExpr> {
            markToken(findTokenFor(value), Style.OTHER_LITERAL)
        }
        add<ForStmt> {
            markKeyword("for")
            markNodes(initialization)
            markNode(compare)
            markNodes(update)
            markNode(body)
        }
        add<ArrayAccessExpr> {
            markNode(name)
            markNode(index)
        }
        add<TypeParameter> {
            markNodes(annotations)
            markSimpleName(name)
            if (!typeBound.isNullOrEmpty()) {
                markKeyword("extends")
                markNodes(typeBound)
            }
        }
        add<EnclosedExpr> {
            markNode(inner)
        }
        val handleSwitch: NodeMarker<SwitchNode> = {
            // drag node type into `this`
            this as Node
            markKeyword("switch")
            markNode(selector)
            markNodes(entries)
        }
        add<SwitchStmt>(handleSwitch)
        add<SwitchExpr>(handleSwitch)
        add<SwitchEntry> {
            if (labels.isNullOrEmpty()) {
                markKeyword("default")
            } else {
                markKeyword("case")
                markNodes(labels)
            }
            markNodes(statements)
        }
        add<InstanceOfExpr> {
            markNode(expression)
            markKeyword("instanceof")
            markNode(type)
        }
        add<InitializerDeclaration> {
            if (isStatic) {
                markKeyword("static")
            }
            markNode(body)
        }
        add<ArrayCreationExpr> {
            markKeyword("new")
            markNode(elementType)
            markNodes(levels)
            markNode(initializer)
        }
        add<ArrayCreationLevel> {
            markNodes(annotations)
            markNode(dimension)
        }
        add<ArrayInitializerExpr> {
            markNodes(values)
        }
        add<BreakStmt> {
            markKeyword("break")
            markNode(value)
        }
        add<EnumDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)
            markKeyword("enum")
            markSimpleName(name)

            if (implementedTypes.isNonEmpty) {
                markKeyword("implements")
                markNodes(implementedTypes)
            }

            markNodes(entries)
            markNodes(members)
        }
        add<EnumConstantDeclaration> {
            markNodes(annotations)
            markSimpleName(name)
            markNodes(arguments)
            markNodes(classBody)
        }
        add<EmptyStmt>(NO_OP)
        add<WhileStmt> {
            markKeyword("while")
            markNode(condition)
            markNode(body)
        }
        add<SingleMemberAnnotationExpr> {
            markKeyword("@")
            markDotSeparatedName(name, fqn = nodeTypeFinder.requireType(nameAsString, this))
            markNode(memberValue)
        }
        add<CharLiteralExpr> {
            markToken(tokens.first { it.kind == GeneratedJavaParserConstants.CHARACTER_LITERAL },
                Style.STRING_LITERAL)
        }
        add<ContinueStmt> {
            markKeyword("continue")
            label.ifPresent { markSimpleName(it) }
        }
        add<LabeledStmt> {
            markSimpleName(label)
            markNode(statement)
        }
        add<SynchronizedStmt> {
            markKeyword("synchronized")
            markNode(expression)
            markNode(body)
        }
        add<UnionType> {
            markNodes(annotations)
            markNodes(elements)
        }
        add<NormalAnnotationExpr> {
            markKeyword("@")
            markDotSeparatedName(name, fqn = nodeTypeFinder.requireType(nameAsString, this))
        }
        add<AnnotationDeclaration> {
            markNodes(annotations)
            markNodes(modifiers)

            markKeyword("@")
            markKeyword("interface")
            markSimpleName(name)
            markNodes(members)
        }
        add<AssertStmt> {
            markKeyword("assert")
            markNode(check)
            markNode(message)
        }
    }
}
