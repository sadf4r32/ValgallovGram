// @name        Транслит
// @author      @mnstrw (port @sadf4r32)
// @version     1.0
// @description Команды .t .tr .b .i .u .s .code .caps .alt .mock .rev .space .sp .bold .italic .mono — режимы трансформации исходящих сообщений

// Port of the AyuGram tr.plugin (Python) to ValgallovGram's JavaScript
// plugin API. Every `.command` toggles a transformation mode; subsequent
// non-command messages you send are passed through the active modes.
// Entity-based modes (bold/italic/underline/strike/mono/spoiler/quote/pre)
// still ship the text as plain but apply Telegram entities via Valgallov.send*.

var modes = {
    tr: false, b: false, i: false, u: false, s: false, code: false,
    caps: false, alt: false, mock: false, rev: false, space: false,
    sp: false, quote: false, pre: false,
    bold: false, italic: false, under: false, strike: false, mono: false
};
var prefix = "";
var suffix = "";

var HELP =
"• .tr — транслит\n" +
"• .caps — КАПС\n" +
"• .alt — аЛьТеРнАтИвНыЙ\n" +
"• .mock — рАнДоМнЫй\n" +
"• .rev — реверс\n" +
"• .space — р а з р я ж е н н ы й\n\n" +
"• .b — жирный (unicode)\n" +
"• .i — курсив (unicode)\n" +
"• .u — подчёркнутый (unicode)\n" +
"• .s — зачёркнутый (unicode)\n" +
"• .code — моноширинный (unicode)\n\n" +
"• .bold — **жирный** (entity)\n" +
"• .italic — __курсив__ (entity)\n" +
"• .under — подчёркнутый (entity)\n" +
"• .strike — ~~зачёркнутый~~ (entity)\n" +
"• .mono — `моноширинный` (entity)\n" +
"• .sp — ||спойлер||\n" +
"• .quote — > цитата\n" +
"• .pre — ```блок кода```\n\n" +
"• .pref текст — префикс\n" +
"• .suf текст — суффикс\n" +
"• .status — что включено\n" +
"• .off — выключить всё\n" +
"• .t — помощь";

var MODE_KEYS = ["tr","b","i","u","s","code","caps","alt","mock","rev","space",
                 "sp","quote","pre","bold","italic","under","strike","mono"];

function transformSimple(text) {
    var t = text;
    if (modes.tr)    t = Valgallov.text.translit(t);
    if (modes.caps)  t = Valgallov.text.caps(t);
    if (modes.alt)   t = Valgallov.text.alt(t);
    if (modes.mock)  t = Valgallov.text.mock(t);
    if (modes.rev)   t = Valgallov.text.reverse(t);
    if (modes.space) t = Valgallov.text.space(t);
    // unicode-math styles
    if (modes.b)     t = Valgallov.text.bold(t);
    if (modes.i)     t = Valgallov.text.italic(t);
    if (modes.u)     t = Valgallov.text.underline(t);
    if (modes.s)     t = Valgallov.text.strike(t);
    if (modes.code)  t = Valgallov.text.mono(t);
    return t;
}

function onSend(dialogId, msg) {
    if (!msg) return null;

    if (msg === ".t") return HELP;

    if (msg === ".off") {
        for (var k in modes) modes[k] = false;
        prefix = "";
        suffix = "";
        return "всё выключено";
    }

    if (msg === ".status") {
        var active = [];
        for (var k in modes) if (modes[k]) active.push(k);
        if (prefix) active.push("pref:" + prefix);
        if (suffix) active.push("suf:" + suffix);
        return active.length ? active.join(", ") : "всё выключено";
    }

    if (msg.indexOf(".pref ") === 0) { prefix = msg.substring(6); return "префикс: " + prefix; }
    if (msg === ".pref")             { prefix = "";               return "префикс: ✗"; }
    if (msg.indexOf(".suf ") === 0)  { suffix = msg.substring(5); return "суффикс: " + suffix; }
    if (msg === ".suf")              { suffix = "";               return "суффикс: ✗"; }

    // toggle single-mode commands
    if (msg.length > 1 && msg.charAt(0) === '.') {
        var key = msg.substring(1);
        for (var i = 0; i < MODE_KEYS.length; i++) {
            if (MODE_KEYS[i] === key) {
                modes[key] = !modes[key];
                return key + ": " + (modes[key] ? "✓" : "✗");
            }
        }
    }

    // Apply transformations
    var text = transformSimple(msg);
    if (prefix) text = prefix + " " + text;
    if (suffix) text = text + " " + suffix;

    // Entity-based overrides take precedence — send explicit formatted message and cancel original
    if (modes.bold)   { Valgallov.sendBold(dialogId, text);   return Valgallov.CANCEL; }
    if (modes.italic) { Valgallov.sendItalic(dialogId, text); return Valgallov.CANCEL; }
    if (modes.under)  { Valgallov.sendUnderline(dialogId, text); return Valgallov.CANCEL; }
    if (modes.strike) { Valgallov.sendStrike(dialogId, text); return Valgallov.CANCEL; }
    if (modes.mono)   { Valgallov.sendCode(dialogId, text);   return Valgallov.CANCEL; }
    if (modes.sp)     { Valgallov.sendSpoiler(dialogId, text); return Valgallov.CANCEL; }
    if (modes.pre)    { Valgallov.sendMono(dialogId, text);   return Valgallov.CANCEL; }
    if (modes.quote)  { return "> " + text; }

    return text === msg ? null : text;
}
