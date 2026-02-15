package com.nboard.ime

val DEFAULT_TOP_EMOJIS = listOf("😀", "😂", "❤️", "🔥", "😭", "👍", "🥳", "✨")
val EMOJI_SCAN_RANGES = listOf(
        0x203C..0x3299,
        0x1F000..0x1FAFF
    )
val KEYCAP_EMOJIS = listOf(
        "#️⃣", "*️⃣", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣"
    )

val AZERTY_ROW_1 = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
val AZERTY_ROW_2 = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
val AZERTY_ROW_3 = listOf("w", "x", "c", "v", "b", "n", ",")
val GBOARD_AZERTY_ROW_3 = listOf("w", "x", "c", "v", "b", "n", "'")

val QWERTY_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
val QWERTY_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
val QWERTY_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m", ",", "'")
val GBOARD_QWERTY_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
val GBOARD_QWERTY_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m", "'")

val VARIANT_MAP = mapOf(
        "a" to listOf("à", "â", "ä", "æ", "á", "ã", "å"),
        "e" to listOf("é", "è", "ê", "ë", "€", "ē"),
        "i" to listOf("î", "ï", "ì", "í", "ī"),
        "o" to listOf("ô", "ö", "œ", "ò", "ó", "õ", "ø"),
        "u" to listOf("ù", "û", "ü", "ú", "ū"),
        "c" to listOf("ç"),
        "n" to listOf("ñ", "ń"),
        "y" to listOf("ÿ", "ý"),
        "'" to listOf("’", "ʼ", "`", "´"),
        "\"" to listOf("«", "»", "“", "”"),
        "." to listOf("!", "?", ";", "…", "•", "·"),
        "," to listOf(".", ";", ":", "…", "!", "?", "'"),
        "-" to listOf("-", "–", "—", "•")
    )

val SMART_TYPING_SENTENCE_ENDERS = setOf('.', '!', '?')
val AUTOCORRECT_TRIGGER_DELIMITERS = setOf(' ', '.', ',', '!', '?', ';', ':', '\n')
val VOWELS_FOR_REPEAT = setOf('a', 'e', 'i', 'o', 'u', 'y')
val DIACRITIC_REGEX = Regex("\\p{M}+")
val ASSET_WORD_REGEX = Regex("[a-zàâäéèêëîïôöùûüçœæÿ'\\-]{2,24}")
val WORD_TOKEN_REGEX = Regex("[\\p{L}][\\p{L}'’\\-]*")
val WHITESPACE_REGEX = Regex("\\s+")

val ENGLISH_WORDS = setOf(
        "a","about","after","again","all","also","always","am","an","and","any","are","around","as","at",
        "back","be","because","been","before","being","best","better","both","but","by","can","could",
        "day","did","do","does","doing","done","dont","down","each","even","every","for","from","get","go",
        "good","great","had","has","have","he","hello","help","her","here","him","his","how","i","if","in",
        "into","is","it","its","just","know","language","last","let","like","little","long","look","make",
        "many","me","more","most","much","my","need","new","next","no","not","now","of","on","one","only",
        "or","other","our","out","over","people","please","right","same","say","see","she","should","small",
        "so","some","something","start","still","such","take","text","than","thank","that","the","their",
        "them","then","there","these","they","thing","this","time","to","today","too","try","two","up","us",
        "use","very","want","was","way","we","well","were","what","when","where","which","who","why","will",
        "with","word","work","would","write","yes","you","your","yours"
    )

val FRENCH_WORDS = setOf(
        "a","à","abord","afin","ai","aie","ainsi","alors","apres","après","au","aucun","aussi","autre","aux",
        "avoir","avec","beaucoup","bien","bon","bonjour","car","ce","cela","celle","celui","ces","cet","cette",
        "chaque","chez","comme","comment","dans","de","des","deux","devant","donc","du","elle","elles","en",
        "encore","entre","est","et","ete","été","etre","être","fait","faire","faut","grand","gros","ici","il",
        "ils","je","jour","juste","la","le","les","leur","leurs","lui","ma","mais","me","mes","mieux","moins",
        "mon","mot","mots","ne","ni","non","nos","notre","nous","nouveau","ou","où","par","parce","pas","peu",
        "plus","pour","pourquoi","premier","quand","que","quel","quelle","quelles","quels","qui","quoi","sa",
        "sans","se","ses","si","son","sont","sur","ta","te","tes","text","texte","tes","toi","ton","toujours",
        "tout","tous","tres","très","tu","un","une","votre","vous","vu","y","salut",
        "fleur","fleurs","fleurir","jolie","magnifique","maison","chat","chien","amour","merci"
    )

val FRENCH_DEFAULT_PREDICTIONS = listOf(
        "je", "vous", "nous", "le", "la", "de", "et", "pour", "avec", "dans"
    )
val ENGLISH_DEFAULT_PREDICTIONS = listOf(
        "i", "you", "we", "the", "to", "and", "for", "with", "in", "on"
    )
val MIXED_DEFAULT_PREDICTIONS = listOf(
        "je", "i", "vous", "you", "le", "the", "de", "to", "et", "and"
    )

val FRENCH_CONTEXT_HINTS = mapOf(
        "*" to listOf("je", "vous", "le", "la", "de", "et", "pour"),
        "je" to listOf("suis", "vais", "peux", "veux", "ne"),
        "tu" to listOf("es", "vas", "peux", "veux", "as"),
        "il" to listOf("est", "a", "va", "peut", "fait"),
        "elle" to listOf("est", "a", "va", "peut", "fait"),
        "nous" to listOf("sommes", "avons", "allons", "pouvons", "voulons"),
        "vous" to listOf("etes", "avez", "allez", "pouvez", "voulez"),
        "ils" to listOf("sont", "ont", "vont", "peuvent", "font"),
        "elles" to listOf("sont", "ont", "vont", "peuvent", "font"),
        "c'est" to listOf("important", "possible", "vrai", "bien", "la"),
        "de" to listOf("la", "le", "l'", "mon", "ton", "notre"),
        "des" to listOf("gens", "choses", "mots", "fleurs", "idées"),
        "un" to listOf("peu", "jour", "mot", "chat", "chien"),
        "une" to listOf("fois", "phrase", "idee", "maison", "fleur"),
        "le" to listOf("temps", "texte", "chat", "monde", "moment"),
        "la" to listOf("vie", "maison", "phrase", "fleur", "question"),
        "les" to listOf("gens", "mots", "choses", "jours", "fleurs"),
        "dans" to listOf("le", "la", "les", "un", "une"),
        "pour" to listOf("le", "la", "vous", "nous", "faire"),
        "je suis" to listOf("en", "a", "avec", "d'accord", "pret"),
        "il est" to listOf("important", "possible", "temps", "la", "vraiment"),
        "nous sommes" to listOf("en", "la", "pret", "ici", "ensemble"),
        "vous etes" to listOf("en", "la", "pret", "ici", "sur")
    )

val ENGLISH_CONTEXT_HINTS = mapOf(
        "*" to listOf("i", "you", "the", "to", "and", "for", "with"),
        "i" to listOf("am", "have", "will", "can", "need"),
        "you" to listOf("are", "have", "can", "will", "should"),
        "he" to listOf("is", "has", "will", "can", "was"),
        "she" to listOf("is", "has", "will", "can", "was"),
        "we" to listOf("are", "have", "can", "will", "need"),
        "they" to listOf("are", "have", "can", "will", "were"),
        "the" to listOf("best", "same", "text", "time", "way"),
        "to" to listOf("the", "be", "do", "go", "make"),
        "for" to listOf("the", "you", "me", "this", "that"),
        "in" to listOf("the", "a", "this", "my", "your"),
        "on" to listOf("the", "my", "your", "this", "that"),
        "a" to listOf("new", "good", "little", "great", "small"),
        "an" to listOf("idea", "example", "email", "issue", "answer"),
        "i am" to listOf("going", "not", "ready", "here", "sure"),
        "i have" to listOf("to", "a", "been", "no", "the"),
        "you are" to listOf("the", "not", "going", "right", "welcome"),
        "we are" to listOf("going", "not", "ready", "here", "the")
    )

val FRENCH_TYPOS = mapOf(
        "salot" to "salut",
        "bjr" to "bonjour",
        "stp" to "s'il te plaît",
        "svp" to "s'il vous plaît"
    )

val ENGLISH_TYPOS = mapOf(
        "teh" to "the",
        "woudl" to "would",
        "dont" to "don't"
    )

val ALL_EMOJIS = listOf(
        "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
        "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥","😶‍🌫️","😏",
        "😒","🙄","😬","😮‍💨","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵",
        "🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧",
        "😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀",
        "☠️","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾",
        "🙈","🙉","🙊","💋","💌","💘","💝","💖","💗","💓","💞","💕","💟","❣️","💔","❤️","🧡","💛","💚","💙",
        "💜","🤎","🖤","🤍","💯","💢","💥","💫","💦","💨","🕳️","💣","💬","🗨️","🗯️","💭","💤",
        "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
        "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
        "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁️","👅","👄",
        "👶","🧒","👦","👧","🧑","👱","👨","🧔","🧔‍♂️","🧔‍♀️","👨‍🦰","👨‍🦱","👨‍🦳","👨‍🦲","👩","👩‍🦰","👩‍🦱","👩‍🦳","👩‍🦲","🧓",
        "👴","👵","🙍","🙍‍♂️","🙍‍♀️","🙎","🙎‍♂️","🙎‍♀️","🙅","🙅‍♂️","🙅‍♀️","🙆","🙆‍♂️","🙆‍♀️","💁","💁‍♂️","💁‍♀️","🙋","🙋‍♂️","🙋‍♀️",
        "🧏","🧏‍♂️","🧏‍♀️","🙇","🙇‍♂️","🙇‍♀️","🤦","🤦‍♂️","🤦‍♀️","🤷","🤷‍♂️","🤷‍♀️","👨‍⚕️","👩‍⚕️","👨‍🎓","👩‍🎓","👨‍🏫","👩‍🏫","👨‍💻","👩‍💻",
        "👨‍🔧","👩‍🔧","👨‍🍳","👩‍🍳","👨‍🚀","👩‍🚀","👨‍⚖️","👩‍⚖️","👮","👮‍♂️","👮‍♀️","🕵️","🕵️‍♂️","🕵️‍♀️","💂","💂‍♂️","💂‍♀️","🥷","👷","👷‍♂️",
        "👷‍♀️","👸","🤴","👳","👳‍♂️","👳‍♀️","👲","🧕","🤵","🤵‍♂️","🤵‍♀️","👰","👰‍♂️","👰‍♀️","🤰","🫃","🫄","🤱","👩‍🍼","👨‍🍼",
        "🎉","🎊","🎈","🎁","🎂","🍰","🧁","🍾","🥂","🍻","🍺","🍷","🥃","🍸","🍹","🧉","☕","🫖","🍫","🍿",
        "🍎","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒",
        "🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🥓","🥩","🍗",
        "🍖","🌭","🍔","🍟","🍕","🌮","🌯","🥙","🧆","🥪","🌭","🍜","🍝","🍣","🍱","🍛","🍤","🍙","🍚","🍘",
        "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🏓","🏸","🥅","🏒","🏑","🥍","🏏","🪃","🥊","🥋",
        "🎮","🕹️","🎲","♟️","🧩","🎯","🎳","🎭","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🎸","🎻","🎺","🪗","🎷",
        "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🚚","🚛","🚜","🏍️","🛵","🚲","🛴","🚨","✈️","🛫",
        "🛬","🚀","🛸","🚁","⛵","🚤","🛥️","🚢","⛴️","🚂","🚆","🚇","🚝","🚟","🚡","🚠","🗽","🗼","🗿","🗺️",
        "🌍","🌎","🌏","🗻","🏕️","🏖️","🏜️","🏝️","🏛️","🏟️","🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏪","🏫",
        "⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","🧮","📷","📸","📹","🎥","📞","☎️","📟","📠","📺","📻","🎙️","🔋",
        "🔌","💡","🔦","🕯️","🧯","🧲","💸","💰","💳","🪙","💎","🔑","🗝️","🔒","🔓","🔐","🧰","🛠️","⚙️","🔧",
        "✅","☑️","✔️","❌","❎","➕","➖","➗","✖️","♾️","‼️","⁉️","❓","❔","❕","❗","〰️","➰","➿","⭕",
        "🌟","✨","⚡","🔥","💧","🌈","☀️","🌤️","⛅","☁️","🌧️","⛈️","🌩️","❄️","☃️","🌊","🍀","🌸","🌹","🌻"
    )

val EMOJI_KEYWORDS = mapOf(
        "😀" to "grin happy smile",
        "😂" to "laugh lol",
        "😭" to "cry tears",
        "❤️" to "heart love",
        "🔥" to "fire hot",
        "👍" to "thumbs up yes ok",
        "🙏" to "pray thanks please",
        "👏" to "clap applause",
        "🎉" to "party celebration",
        "✨" to "sparkles",
        "😡" to "angry mad",
        "🤔" to "thinking",
        "😴" to "sleep",
        "🥳" to "party hat",
        "🤝" to "handshake",
        "✅" to "check valid",
        "💡" to "idea",
        "⚽" to "football sport",
        "🍕" to "pizza food",
        "🍔" to "burger food",
        "🍟" to "fries food",
        "☕" to "coffee",
        "🚗" to "car",
        "✈️" to "plane travel",
        "🌧️" to "rain",
        "🌞" to "sun",
        "🌈" to "rainbow",
        "🧠" to "brain",
        "💻" to "computer",
        "📱" to "phone",
        "📸" to "camera",
        "💯" to "hundred perfect",
        "💸" to "money",
        "🕘" to "latest recent"
    )
