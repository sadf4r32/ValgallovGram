// @name        Typos (replace telegram → ValgallovGram)
// @author      sadf4r32
// @version     1.0
// @description Автоматически заменяет «telegram»/«телеграм» → «ValgallovGram»

var REPLACEMENTS = [
    [/\btelegram\b/gi, "ValgallovGram"],
    [/\bтелеграм\b/gi, "ValgallovGram"],
    [/\bтелега\b/gi, "Вальгалла"]
];

function onSend(dialogId, text) {
    var out = text;
    for (var i = 0; i < REPLACEMENTS.length; i++) {
        out = out.replace(REPLACEMENTS[i][0], REPLACEMENTS[i][1]);
    }
    return out === text ? null : out;
}
