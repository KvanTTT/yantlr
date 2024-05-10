package atn

class Atn(
    val modeStartStates: List<ModeState>,
    val lexerStartStates: List<RuleState>,
    val parserStartStates: List<RuleState>
)