fun main(args: Array<String>) {
    TestGenerator(args.firstOrNull() ?: "").generateGrammarTests()
}