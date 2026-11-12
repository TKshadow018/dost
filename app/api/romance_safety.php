<?php

function dost_romance_allowed_by_age($userAge, $friendAge)
{
    foreach (array($userAge, $friendAge) as $age) {
        if (!is_string($age)) {
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
        '/\b(?:love|loves|loved|loving|romance|romantic|crush|kiss|kissing|date|dating|girlfriend|boyfriend|fianc(?:e|é)e?|marry|married|marriage|husband|wife|darling|sweetheart|flirt|flirting|intimate|intimacy)\b|'
        . '\b(?:make\s+out|hook\s+up|go\s+out\s+with\s+(?:you|me)|fall(?:ing)?\s+in\s+love)\b|'
        . 'ভালোবাসা|ভালোবাসি|প্রেম|রোমান্স|রোমান্টিক|চুমু|চুম্বন|ডেটিং|গার্লফ্রেন্ড|বয়ফ্রেন্ড|প্রেমিকা|প্রেমিক|ফ্লার্ট|'
        . 'प्यार|प्रेम|रोमांस|रोमांटिक|चूम|चुंबन|डेटिंग|गर्लफ्रेंड|बॉयफ्रेंड|प्रेमिका|प्रेमी|फ्लर्ट|शादी/iu',
        $text
    );
    $sexual = preg_match(
        '/\b(?:sex|sexual|sext(?:ing)?|porn|nude|naked|genitals?|penis|vagina|orgasm|erotic|aroused|intercourse)\b|'
        . 'যৌন|সেক্স|নগ্ন|অশ্লীল|শারীরিক সম্পর্ক|যৌনতা|'
        . 'यौन|सेक्स|नग्न|अश्लील|शारीरिक संबंध|पोर्न/iu',
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
    foreach ($messages as $message) {
        if (!is_array($message) || !isset($message['role'], $message['content']) || !is_string($message['content'])) {
            return true;
        }
        if (!in_array($message['role'], array('user', 'assistant'), true)) {
            continue;
        }
        if (!$romanceAllowed && isset($message['image_data'])) {
            return true;
        }
        if (dost_content_requires_safety_block($message['content'], $romanceAllowed)) {
            return true;
        }
    }
    return false;
}

function dost_safety_fallback($romanceAllowed)
{
    return $romanceAllowed
        ? 'I can keep our conversation respectful and non-explicit.'
        : "Let's keep our conversation friendly and age-appropriate.";
}
