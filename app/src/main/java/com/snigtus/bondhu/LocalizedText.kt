package com.snigtus.dost

import java.util.Locale

internal enum class LocalizedText(
    private val english: String,
    private val bangla: String,
    private val hindi: String
) {
    ADD_FRIEND_TITLE("Add a friend", "একজন বন্ধু যোগ করুন", "एक दोस्त जोड़ें"),
    FRIEND_NAME("Name", "নাম", "नाम"),
    FRIEND_AGE("Age", "বয়স", "उम्र"),
    FRIEND_TIMEZONE("Friend timezone", "বন্ধুর সময় অঞ্চল", "दोस्त का समय क्षेत्र"),
    DAILY_BUSY_SCHEDULE("Daily busy schedule", "দৈনিক ব্যস্ততার সময়সূচি", "दैनिक व्यस्त समय-सारणी"),
    SCHOOL("School", "স্কুল", "स्कूल"),
    WORK("Work", "কাজ", "काम"),
    SCHEDULE_STARTS("Starts %s", "%s-এ শুরু", "%s पर शुरू"),
    SCHEDULE_DURATION("%dh", "%d ঘণ্টা", "%d घंटे"),
    SCHEDULE_HOURS("%d hours", "%d ঘণ্টা", "%d घंटे"),
    SCHEDULE_UNTIL("until %s", "%s পর্যন্ত", "%s तक"),
    BUSY_SCHEDULE_NOTE(
        "Busy times use %s local time. Replies resume 30 minutes after the busy window.",
        "ব্যস্ততার সময় %s স্থানীয় সময় অনুযায়ী। ব্যস্ততার সময় শেষ হওয়ার ৩০ মিনিট পর উত্তর আবার শুরু হবে।",
        "व्यस्त समय %s स्थानीय समय के अनुसार है। व्यस्त अवधि समाप्त होने के 30 मिनट बाद जवाब फिर से आने लगेंगे।"
    ),
    NO_PHOTO_SELECTED("No photo selected", "কোনো ছবি নির্বাচন করা হয়নি", "कोई फ़ोटो नहीं चुनी गई"),
    PHOTO_SELECTED("Photo selected", "ছবি নির্বাচন করা হয়েছে", "फ़ोटो चुनी गई"),
    CHOOSE_PHOTO("Choose photo", "ছবি বেছে নিন", "फ़ोटो चुनें"),
    CHANGE_PHOTO("Change photo", "ছবি বদলান", "फ़ोटो बदलें"),
    REMOVE("Remove", "সরান", "हटाएँ"),
    ADD("Add", "যোগ করুন", "जोड़ें"),
    CANCEL("Cancel", "বাতিল", "रद्द करें"),
    CLOSE("Close", "বন্ধ করুন", "बंद करें"),
    YOUR_TIMEZONE("Your timezone", "আপনার সময় অঞ্চল", "आपका समय क्षेत्र"),
    AI_PROVIDER_MODEL("AI provider and model", "AI প্রদানকারী ও মডেল", "AI प्रदाता और मॉडल"),
    CHAT_ARCHIVE_NOTICE(
        "Chat messages and AI replies are saved in a private server archive.",
        "চ্যাটের বার্তা এবং AI-এর উত্তর একটি ব্যক্তিগত সার্ভার আর্কাইভে সংরক্ষণ করা হয়।",
        "चैट संदेश और AI के जवाब निजी सर्वर संग्रह में सहेजे जाते हैं।"
    ),
    OPENROUTER_API_KEY("Optional personal OpenRouter API key", "ঐচ্ছিক ব্যক্তিগত OpenRouter API কী", "वैकल्पिक निजी OpenRouter API कुंजी"),
    SERVER_KEY_PLACEHOLDER("Leave blank to use Dost's configured server key", "Dost-এর সার্ভার কী ব্যবহার করতে খালি রাখুন", "Dost की सर्वर कुंजी इस्तेमाल करने के लिए खाली छोड़ें"),
    API_KEY_GUIDANCE(
        "Optional. Dost's configured server key is used unless you save a personal key here.",
        "ঐচ্ছিক। এখানে ব্যক্তিগত কী সংরক্ষণ না করলে Dost-এর সার্ভার কী ব্যবহার হবে।",
        "वैकल्पिक। यहाँ निजी कुंजी सेव न करने पर Dost की सर्वर कुंजी इस्तेमाल होगी।"
    ),
    HIDE("Hide", "লুকান", "छिपाएँ"),
    SHOW("Show", "দেখান", "दिखाएँ"),
    PREFERRED_MODEL("Preferred OpenRouter model", "পছন্দের OpenRouter মডেল", "पसंदीदा OpenRouter मॉडल"),
    MODEL_MAY_BE_SLOW("(may be slow)", "(ধীর হতে পারে)", "(धीमा हो सकता है)"),
    MODEL_GUIDANCE(
        "Choose a model from the list or type any OpenRouter model ID. Your choice is saved as the preferred model. Photos require a vision-capable model. Paid or custom models require your own key; the key is encrypted on this device and never written to logs.",
        "তালিকা থেকে একটি মডেল বেছে নিন অথবা যেকোনো OpenRouter মডেল আইডি লিখুন। আপনার পছন্দের মডেলটি সংরক্ষণ করা হবে। ছবি ব্যবহারের জন্য ছবি বুঝতে পারে এমন মডেল দরকার। পেইড বা কাস্টম মডেলের জন্য আপনার নিজস্ব কী লাগবে; কীটি এই ডিভাইসে এনক্রিপ্ট করে রাখা হয় এবং কখনো লগে লেখা হয় না।",
        "सूची से मॉडल चुनें या OpenRouter का कोई भी मॉडल ID लिखें। आपकी पसंद को पसंदीदा मॉडल के रूप में सहेजा जाएगा। फ़ोटो का विश्लेषण करने के लिए विज़न-सक्षम मॉडल ज़रूरी है। सशुल्क या कस्टम मॉडल के लिए आपकी अपनी कुंजी चाहिए; यह कुंजी इस डिवाइस पर एन्क्रिप्ट होती है और लॉग में कभी नहीं लिखी जाती।"
    ),
    MODEL_FORMAT_ERROR(
        "Enter a model ID in provider/model format.",
        "provider/model ফরম্যাটে একটি মডেল আইডি লিখুন।",
        "provider/model फ़ॉर्मेट में मॉडल ID लिखें।"
    ),
    SAVE("Save", "সংরক্ষণ", "सहेजें"),
    SEARCH("Search", "খুঁজুন", "खोजें"),
    EXPORT_CHAT("Export chat", "চ্যাট রপ্তানি করুন", "चैट निर्यात करें"),
    YOUR_PROFILE("Your profile", "আপনার প্রোফাইল", "आपकी प्रोफ़ाइल"),
    SESSION_SUMMARIES("Session summaries", "সেশনের সারাংশ", "सत्रों का सारांश"),
    MINIMIZE_CHAT("Minimize chat", "চ্যাট মিনিমাইজ করুন", "चैट छोटा करें"),
    COPY("Copy", "কপি করুন", "कॉपी करें"),
    REPLY_DIFFERENTLY("Reply differently", "অন্যভাবে উত্তর দিন", "अलग तरीके से जवाब दें"),
    ADD_FRIEND_FIRST("Add a friend in Dost first", "আগে Dost-এ একজন বন্ধু যোগ করুন", "पहले Dost में एक दोस्त जोड़ें"),
    CHAT_WITH_FRIEND("Chat with %s", "%s-এর সঙ্গে চ্যাট", "%s के साथ चैट"),
    DRAG_TO_CLOSE("Drag here to close", "বন্ধ করতে এখানে টানুন", "बंद करने के लिए यहाँ खींचें"),
    MESSAGE("Message", "বার্তা", "संदेश"),
    SEND("Send", "পাঠান", "भेजें"),
    FLOATING_CHAT_TITLE("Dost floating chat", "Dost ভাসমান চ্যাট", "Dost फ़्लोटिंग चैट"),
    LOAD_FRIENDS_FAILED("Could not load friends.", "বন্ধুদের তালিকা আনা যায়নি।", "दोस्तों की सूची लोड नहीं हो सकी।"),
    INVALID_FRIEND_CATALOG("The friends catalog returned invalid data.", "বন্ধুদের তালিকায় ভুল তথ্য এসেছে।", "दोस्तों की सूची में अमान्य जानकारी मिली।"),
    ADD_SYSTEM_FRIEND_FAILED("Could not add this friend.", "এই বন্ধুকে যোগ করা যায়নি।", "इस दोस्त को नहीं जोड़ा जा सका।"),
    INVALID_SYSTEM_FRIEND("This friend profile is invalid.", "এই বন্ধুর প্রোফাইলটি সঠিক নয়।", "इस दोस्त की प्रोफ़ाइल अमान्य है।");

    fun resolve(language: AppLanguage, vararg arguments: Any): String {
        val text = when (language) {
            AppLanguage.ENGLISH -> english
            AppLanguage.BANGLA -> bangla
            AppLanguage.HINDI -> hindi
        }
        return if (arguments.isEmpty()) text else String.format(Locale.ROOT, text, *arguments)
    }
}
