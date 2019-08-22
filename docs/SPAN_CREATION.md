Span Creation
=============

The problem is, how do we map Java text into MCP-mapped and highlighted RichTextFX content?

Here's the current approach (pseudo code):

```kotlin
fun convert(input: Document) {
    // Parse Tokens
    val tokens = tokenize(input.text)
    // Replace mappings in these tokens
    // produces "styles" with SRG names, text, no actual style info
    val mappedStyles = remap(tokens)
    // Re-write the original document with the new style/text
    mappedStyles.writeToDoc(input)
    // Parse from the mapped styles
    val ast = javaParser.parse(provider(input.text))
    // Mark styles & jump-targets in the document
    ast.markDocument(input)
}
```
