package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class BeginnerSetTest {
    @Test
    fun `набор — 52 тройки British Council и русские значения`() {
        assertEquals(expected, beginnerVerbs)
    }
}

private val expected = listOf(
    Verb("быть", Forms("be", "was", "been")),
    Verb("начинаться", Forms("begin", "began", "begun")),
    Verb("ломать", Forms("break", "broke", "broken")),
    Verb("приносить", Forms("bring", "brought", "brought")),
    Verb("покупать", Forms("buy", "bought", "bought")),
    Verb("строить", Forms("build", "built", "built")),
    Verb("выбирать", Forms("choose", "chose", "chosen")),
    Verb("приходить", Forms("come", "came", "come")),
    Verb("стоить", Forms("cost", "cost", "cost")),
    Verb("резать", Forms("cut", "cut", "cut")),
    Verb("делать", Forms("do", "did", "done")),
    Verb("рисовать", Forms("draw", "drew", "drawn")),
    Verb("ехать", Forms("drive", "drove", "driven")),
    Verb("есть", Forms("eat", "ate", "eaten")),
    Verb("чувствовать", Forms("feel", "felt", "felt")),
    Verb("находить", Forms("find", "found", "found")),
    Verb("приобретать", Forms("get", "got", "got")),
    Verb("давать", Forms("give", "gave", "given")),
    Verb("идти", Forms("go", "went", "gone")),
    Verb("иметь", Forms("have", "had", "had")),
    Verb("слышать", Forms("hear", "heard", "heard")),
    Verb("держать", Forms("hold", "held", "held")),
    Verb("хранить", Forms("keep", "kept", "kept")),
    Verb("знать", Forms("know", "knew", "known")),
    Verb("уходить", Forms("leave", "left", "left")),
    Verb("вести", Forms("lead", "led", "led")),
    Verb("позволять", Forms("let", "let", "let")),
    Verb("лежать", Forms("lie", "lay", "lain")),
    Verb("терять", Forms("lose", "lost", "lost")),
    Verb("делать или создавать", Forms("make", "made", "made")),
    Verb("значить", Forms("mean", "meant", "meant")),
    Verb("встречать", Forms("meet", "met", "met")),
    Verb("платить", Forms("pay", "paid", "paid")),
    Verb("класть", Forms("put", "put", "put")),
    Verb("бежать", Forms("run", "ran", "run")),
    Verb("говорить или сказать", Forms("say", "said", "said")),
    Verb("видеть", Forms("see", "saw", "seen")),
    Verb("продавать", Forms("sell", "sold", "sold")),
    Verb("посылать", Forms("send", "sent", "sent")),
    Verb("назначать", Forms("set", "set", "set")),
    Verb("сидеть", Forms("sit", "sat", "sat")),
    Verb("говорить", Forms("speak", "spoke", "spoken")),
    Verb("тратить", Forms("spend", "spent", "spent")),
    Verb("стоять", Forms("stand", "stood", "stood")),
    Verb("брать", Forms("take", "took", "taken")),
    Verb("преподавать", Forms("teach", "taught", "taught")),
    Verb("говорить или рассказывать", Forms("tell", "told", "told")),
    Verb("думать", Forms("think", "thought", "thought")),
    Verb("понимать", Forms("understand", "understood", "understood")),
    Verb("носить", Forms("wear", "wore", "worn")),
    Verb("побеждать", Forms("win", "won", "won")),
    Verb("писать", Forms("write", "wrote", "written")),
)
