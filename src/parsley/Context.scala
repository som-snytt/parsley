package parsley

import parsley.Stack._

// Private internals
private [parsley] final class Frame(val ret: Int, val instrs: Array[Instr])
{
    override def toString: String = s"[$instrs@$ret]"
}
private [parsley] final class Handler(val depth: Int, val pc: Int, val stacksz: Int)
{
    override def toString: String = s"Handler@$depth:$pc(-$stacksz)"
}
private [parsley] final class State(val offset: Int, val line: Int, val col: Int)
{
    override def toString: String = s"$offset ($line, $col)"
}

private [parsley] final class Context(var instrs: Array[Instr],
                                      val input: Array[Char],
                                      val subs: Map[String, Array[Instr]])
{
    var stack: Stack[Any] = Stack.empty
    var offset: Int = 0
    val inputsz: Int = input.length
    var calls: Stack[Frame] = Stack.empty
    var states: Stack[State] = Stack.empty
    var stacksz: Int = 0
    var checkStack: Stack[Int] = Stack.empty
    var status: Status = Good
    var handlers: Stack[Handler] = Stack.empty
    var depth: Int = 0
    var pc: Int = 0
    var line: Int = 1
    var col: Int = 1
    var erroffset: Int = -1
    var errcol: Int = -1
    var errline: Int = -1
    var raw: List[String] = Nil
    var unexpected: Option[String] = None
    var expected: List[Option[String]] = Nil
    var errorOverride: Option[String] = None
    var overrideDepth: Int = 0

    override def toString: String =
    {
        s"""|[
            |  stack=[${mkString(stack, ", ")}]
            |  instrs=${instrs.mkString("; ")}
            |  input=${input.drop(offset).mkString}
            |  status=$status
            |  pc=$pc
            |  depth=$depth
            |  rets=${mkString(map[Frame, Int](calls, _.ret), ", ")}
            |  handlers=${mkString(handlers, ":") + "[]"}
            |  recstates=${mkString(handlers, ":") + "[]"}
            |]""".stripMargin
    }

    def fail(e: Option[String] = None): Unit =
    {
        if (isEmpty(handlers))
        {
            status = Failed
            if (erroffset == -1)
            {
                errcol = col
                errline = line
            }
        }
        else
        {
            status = Recover
            val handler = handlers.head
            handlers = handlers.tail
            val diffdepth = depth - handler.depth - 1
            if (diffdepth >= 0)
            {
                val calls_ = if (diffdepth != 0) drop(calls, diffdepth) else calls
                instrs = calls_.head.instrs
                calls = calls_.tail
            }
            pc = handler.pc
            val diffstack = stacksz - handler.stacksz
            if (diffstack > 0) stack = drop(stack, diffstack)
            stacksz = handler.stacksz
            depth = handler.depth
            if (depth < overrideDepth)
            {
                overrideDepth = 0
                errorOverride = None
            }
        }
        if (offset > erroffset)
        {
            erroffset = offset
            errcol = col
            errline = line
            unexpected = if (offset < inputsz) Some("\"" + input(offset).toString + "\"") else Some("end of input")
            expected ::= errorOverride.orElse(e)
        }
        else if (offset == erroffset) expected ::= errorOverride.orElse(e)
    }

    def errorMessage(): String =
    {
        if (unexpected.isDefined)
            s"""(line $errline, column $errcol):
               |  unexpected ${unexpected.get}
               |  expected ${expected.flatten.distinct.reverse.mkString(" or ")}
               |  ${raw.distinct.reverse.mkString(" or ")}""".stripMargin.trim
        else s"(line $errline, column $errcol):\n  ${if (raw.isEmpty) "unknown error" else raw.distinct.reverse.mkString(" or ")})}"
    }

    def inc(): Unit = pc += 1

    // Stack Manipulation Methods
    def pushStack(x: Any): Unit = { stack = new Stack(x, stack); stacksz += 1 }
    def popStack(): Any =
    {
        val ret = stack.head
        stack = stack.tail
        stacksz -= 1
        ret
    }
    def exchangeStack(x: Any): Unit = stack.head = x
}
