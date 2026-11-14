<?php

function dost_romance_allowed_by_age($userAge, $friendAge)
{
    foreach (array($userAge, $friendAge) as $age) {
        if (!is_string($age) || preg_match('/[\x00-\x1F\x7F]/', $age) === 1) {
            return false;
        }
        $age = trim($age);
        if (preg_match('/\A[0-9]{1,3}\z/', $age) !== 1) {
            return false;
        }
        $years = (int) $age;
        if ($years < 18 || $years > 120) {
            return false;
        }
    }
    return true;
}

function dost_classify_romance_safety($text)
{
    if (!is_string($text)) {
        return null;
    }
    $romantic = preg_match(
    // 1. English Single Words (Affection, Relationships, Pet Names, Slang)
    '/\b(?:love[ds]?|loving|lover|romance|romantic(?:ally)?|crush(?:es|ing)?|kiss(?:es|ed|ing)?|smooch(?:ing)?|cuddle|cuddling|snuggle|snuggling|hug(?:s|ged|ging)?|date[ds]?|dating|girlfriend|boyfriend|gf|bf|fianc(?:e|é)e?|marry|married|marriage|spouse|husband|wife|hubby|wifey|soulmate|sweetheart|darling|honey|babe|baby|bae|boo|cutie|hottie|handsome|gorgeous|flirt(?:s|ed|ing|atious)?|intimate|intimacy|affection(?:ate)?|infatuat(?:ed|ion)|passion(?:ate)?|seduce|seducing|seduction|propose|proposal|engagement|engaged|valentine|heartbreak|heartbroken|cheating|affair|situationship|monogam(?:y|ous)|polyamor(?:y|ous)|tinder|bumble|hinge)\b|'

    // 2. English Multi-Word Phrases
    . '\b(?:make\s+out|making\s+out|hook(?:ing)?\s+up|go(?:ing)?\s+out\s+with|ask(?:ing)?\s+(?:you|me|him|her|them)\s+out|fall(?:ing|en)?\s+in\s+love|head\s+over\s+heels|significant\s+other|better\s+half|other\s+half|blind\s+date|speed\s+dating|love\s+at\s+first\s+sight|long\s+distance\s+relationship|hold(?:ing)?\s+hands|tie\s+the\s+knot|pop\s+the\s+question|walk\s+down\s+the\s+aisle|friends\s+with\s+benefits|secret\s+admirer|candlelight\s+dinner|be\s+mine|broken\s+heart)\b|'

    // 3. Hinglish & Banglish (Romanized Slang/Common Terms)
    . '\b(?:pyaar|pyar|ishq|mohabbat|shona|babu|jaan|jaanu|janu|dilruba|mehboob|sanam|ashiq|aashiq|shadi|shaadi|biwi|pati|patni|premi|premika|bhalobasha|valobasha|bhalobashi|valobashi|bou|jamai|porokiya)\b|'

    // 4. Bengali (বাংলা)
    . 'ভালোবাসা|ভালোবাসি|ভালোবেসে|প্রেম|প্রেমিক|প্রেমিকা|রোমান্স|রোমান্টিক|চুমু|চুম্বন|ডেট|ডেটিং|গার্লফ্রেন্ড|বয়ফ্রেন্ড|ফ্লার্ট|বিয়ে|বিবাহ|স্বামী|স্ত্রী|বউ|জামাই|অর্ধাঙ্গিনী|প্রিয়তমা|প্রিয়তম|মন\s+দেয়া\s+নেয়া|হৃদয়|আলিঙ্গন|জড়িয়ে\s+ধরা|পরকীয়া|ক্রাশ|সোহাগ|আদর|জানু|সোনা|বাবু|মধুচন্দ্রিমা|হানিমুন|বাগদান|আংটি\s+বদল|প্রস্তাব|'

    // 5. Hindi (हिन्दी)
    . 'प्यार|प्रेम|इश्क़|इश्क|मोहब्बत|रोमांस|रोमांटिक|चूम|चूमना|चुंबन|डेट|डेटिंग|गर्लफ्रेंड|बॉयफ्रेंड|प्रेमिका|प्रेमी|फ्लर्ट|शादी|विवाह|पति|पत्नी|बीवी|शौहर|महबूब|महबूबा|सनम|आशिक|आशिक़|दिलरुबा|जान|जानू|बाबू|शोना|हमसफर|हमसफ़र|जीवनसाथी|सगाई|रिश्ता|गले\s+लगाना|आलिंगन|दिल\s+देना|क्रश|सुहागरात|हनीमून|बेवफा|बेवफ़ाई|अफेयर/iu',
    $text
);

$sexual = preg_match(
    // 1. English Single Words (Acts, Anatomy, Fetish, Slang, Adult Industry)
    '/\b(?:sex(?:ual|ually|y|iness)?|sext(?:s|ed|ing)?|porn(?:ography|ographic|o)?|xxx|nsfw|nude[s]?|nudity|naked|strip(?:ping|per)?|genital(?:s|ia)?|penis|dick|cock|phallus|testicles?|balls|scrotum|vagina(?:l)?|pussy|vulva|clit(?:oris)?|labia|breasts?|boobs?|tits|titties|nipples?|butt|ass|anus|anal|orgasm(?:ic)?|climax|cum(?:ming)?|ejaculat(?:e|ion|ing)|erotic(?:a)?|arous(?:ed|al|ing)|horny|kinky|fetish|bdsm|bondage|dominatrix|sadomasochism|intercourse|coitus|copulat(?:e|ion)|fornicat(?:e|ion)|masturbat(?:e|ion|ing)|jerk(?:ing)?\s+off|wank(?:ing)?|fap(?:ping)?|dildo|vibrator|fleshlight|condom|contracepti(?:ve|on)|lube|lubricant|viagra|prostitut(?:e|ion)|escort|brothel|whore|slut|hooker|pimp|onlyfans|camgirl|blowjob|handjob|rimjob|footjob|fellatio|cunnilingus|sodomy|threesome|foursome|orgy|gangbang|creampie|milf|dilf|hentai|incest|rape|molest(?:ation)?|harass(?:ment)?|erection|boner|wet\s+dream|libido|foreplay|virgin(?:ity)?|lust(?:ful)?|sensual(?:ity)?|seduction|aphrodisiac|boink|shag(?:ging)?|bang(?:ing)?|screw(?:ing)?|hump(?:ing)?|smash(?:ing)?|booty|twerk(?:ing)?)\b|'

    // 2. English Multi-Word Phrases & Abbreviations
    . '\b(?:one\s+night\s+stand|friends\s+with\s+benefits|fwb|ons|lap\s+dance|strip\s+club|sex\s+toy[s]?|sex\s+tape|make\s+love|making\s+love|sleep(?:ing)?\s+with|go(?:ing)?\s+down\s+on|oral\s+sex|anal\s+sex|cyber\s*sex|phone\s+sex|role\s*play|dirty\s+talk|send\s+nudes|sugar\s+daddy|sugar\s+baby|booty\s+call|netflix\s+and\s+chill|69|doggy\s*style|missionary\s+position|mile\s+high\s+club|happy\s+ending|g\s*spot)\b|'

    // 3. Hinglish & Banglish (Explicit/Adult Romanized Terms)
    . '\b(?:chudai|chodna|lund|lauda|chut|gaand|gand|boob|bra|panty|suhagraat|kamuk|hawas|mutth|muth|randi|veshya|boudi|dhon|gud|magi|choda|chudi|khanki)\b|'

    // 4. Bengali (বাংলা)
    . 'যৌন|যৌনতা|সেক্স|সেক্সি|নগ্ন|নগ্নতা|উলঙ্গ|অশ্লীল|অশ্লীলতা|শারীরিক\s+সম্পর্ক|দৈহিক\s+মিলন|সহবাস|মিলন|কাম|কামুক|কামোত্তেজক|যৌনাঙ্গ|লিঙ্গ|যোনি|স্তন|দুধ|বুক|নিতম্ব|পাছা|বীর্য|বীর্যপাত|অর্গাজম|হস্তমৈথুন|পর্ন|পর্নোগ্রাফি|নীল\s+ছবি|চটি|রতি|রতিক্রিয়া|ধর্ষণ|যৌন\s+হয়রানি|পতিতা|বেশ্যা|পতিতালয়|কনডম|গর্ভনিরোধক|ধোন|গুদ|চোদা|চুদি|মাগী|খানকি|'

    // 5. Hindi (हिन्दी)
    . 'यौन|सेक्स|सेक्सी|नग्न|नग्नता|नंगा|अश्लील|अश्लीलता|शारीरिक\s+संबंध|संभोग|सहवास|मैथुन|काम|कामुक|कामुकता|कामोत्तेजना|हवस|गुप्तांग|जननांग|लिंग|योनि|स्तन|छाती|चूची|नितंब|गांड|वीर्य|स्खलन|ऑर्गेज्म|चरम\s+सुख|हस्तमैथुन|मुठ|पोर्न|पोर्नोग्राफी|ब्लू\s+फिल्म|बलात्कार|यौन\s+शोषण|वेश्या|वेश्यावृत्ति|रंडी|कोठा|कंडोम|गर्भनिरोधक|लंड|लौड़ा|चूत|चोदना|चुदाई/iu',
    $text
);
    if ($romantic === false || $sexual === false) {
        return null;
    }
    return array('romantic' => $romantic === 1, 'sexual' => $sexual === 1);
}

function dost_content_requires_safety_block($text, $romanceAllowed)
{
    $classification = dost_classify_romance_safety($text);
    if (!is_array($classification) || !isset($classification['romantic'], $classification['sexual'])) {
        return true;
    }
    return $classification['sexual'] || (!$romanceAllowed && $classification['romantic']);
}

function dost_conversation_requires_safety_block($messages, $romanceAllowed)
{
    if (!is_array($messages)) {
        return true;
    }
    $latestUserMessage = null;
    foreach ($messages as $message) {
        if (!is_array($message)
            || !isset($message['role'], $message['content'])
            || !in_array($message['role'], array('system', 'user', 'assistant'), true)
            || !is_string($message['content'])
        ) {
            return true;
        }
        if ($message['role'] === 'user') {
            $latestUserMessage = $message;
        }
    }
    if ($latestUserMessage === null) {
        return false;
    }
    if (!$romanceAllowed && array_key_exists('image_data', $latestUserMessage)) {
        return true;
    }
    return dost_content_requires_safety_block($latestUserMessage['content'], $romanceAllowed);
}

function dost_filter_unsafe_conversation_history($messages, $romanceAllowed)
{
    $safeMessages = array();
    $skipNextAssistant = false;
    $lastUserIndex = null;
    foreach ($messages as $message) {
        if ($message['role'] === 'system') {
            if (!$romanceAllowed && array_key_exists('image_data', $message)) {
                continue;
            }
            $safeMessages[] = $message;
            continue;
        }
        if ($message['role'] === 'user') {
            $unsafeImage = !$romanceAllowed && array_key_exists('image_data', $message);
            if ($unsafeImage || dost_content_requires_safety_block($message['content'], $romanceAllowed)) {
                $skipNextAssistant = true;
                $lastUserIndex = null;
                continue;
            }
            $skipNextAssistant = false;
            $safeMessages[] = $message;
            $lastUserIndex = count($safeMessages) - 1;
            continue;
        }
        if (!$romanceAllowed && array_key_exists('image_data', $message)) {
            if ($lastUserIndex !== null) {
                unset($safeMessages[$lastUserIndex]);
                $safeMessages = array_values($safeMessages);
            }
            $lastUserIndex = null;
            continue;
        }
        if ($skipNextAssistant) {
            $skipNextAssistant = false;
            $lastUserIndex = null;
            continue;
        }
        if (dost_content_requires_safety_block($message['content'], $romanceAllowed)) {
            if ($lastUserIndex !== null) {
                unset($safeMessages[$lastUserIndex]);
                $safeMessages = array_values($safeMessages);
            }
        } else {
            $safeMessages[] = $message;
        }
        $lastUserIndex = null;
    }
    return $safeMessages;
}

function dost_safety_fallback($romanceAllowed, $chatLanguage = 'en')
{
    $fallbacks = array(
        'en' => array(
            'romance' => 'I can keep our conversation respectful and non-explicit.',
            'age' => "Let's keep our conversation friendly and age-appropriate."
        ),
        'bn' => array(
            'romance' => 'আমি আমাদের কথোপকথনকে সম্মানজনক ও অশ্লীলতামুক্ত রাখতে পারি।',
            'age' => 'চলুন, আমাদের কথোপকথন বন্ধুত্বপূর্ণ ও বয়স-উপযোগী রাখি।'
        ),
        'hi' => array(
            'romance' => 'हमारी बातचीत सम्मानजनक और गैर-अश्लील रह सकती है।',
            'age' => 'आइए, बातचीत को दोस्ताना और उम्र के हिसाब से उचित रखें।'
        )
    );
    if (!is_string($chatLanguage) || !isset($fallbacks[$chatLanguage])) {
        $chatLanguage = 'en';
    }
    return $fallbacks[$chatLanguage][$romanceAllowed ? 'romance' : 'age'];
}
