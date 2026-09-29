<?php
/**
 * Image Upload API for AJD NGANJ
 * Upload this file to: https://bisque-bear-900175.hostingersite.com/api/upload.php
 * 
 * Create the folder structure on your Hostinger:
 * /public_html/api/upload.php (this file)
 * /public_html/uploads/membership-photos/ (create this folder with 755 permissions)
 */

// Set CORS headers to allow requests from your React app
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');
header('Content-Type: application/json');

// Handle preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// Only allow POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    echo json_encode(['success' => false, 'message' => 'Only POST requests allowed']);
    exit();
}

// Configuration
$allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
$maxFileSize = 5 * 1024 * 1024; // 5MB
$uploadBaseDir = '../uploads/';
$baseUrl = 'https://bisque-bear-900175.hostingersite.com/uploads/';

// Get the target folder from request (default to 'general')
$folder = isset($_POST['folder']) ? preg_replace('/[^a-zA-Z0-9\-_]/', '', $_POST['folder']) : 'general';
$uploadDir = $uploadBaseDir . $folder . '/';

// Create directory if it doesn't exist
if (!is_dir($uploadDir)) {
    mkdir($uploadDir, 0755, true);
}

// Check if file was uploaded
if (!isset($_FILES['image']) || $_FILES['image']['error'] !== UPLOAD_ERR_OK) {
    $errorMessages = [
        UPLOAD_ERR_INI_SIZE => 'File exceeds server limit',
        UPLOAD_ERR_FORM_SIZE => 'File exceeds form limit',
        UPLOAD_ERR_PARTIAL => 'File was only partially uploaded',
        UPLOAD_ERR_NO_FILE => 'No file was uploaded',
        UPLOAD_ERR_NO_TMP_DIR => 'Missing temporary folder',
        UPLOAD_ERR_CANT_WRITE => 'Failed to write file to disk',
        UPLOAD_ERR_EXTENSION => 'Upload stopped by extension',
    ];
    
    $error = isset($_FILES['image']) ? $_FILES['image']['error'] : UPLOAD_ERR_NO_FILE;
    $message = isset($errorMessages[$error]) ? $errorMessages[$error] : 'Unknown upload error';
    
    echo json_encode(['success' => false, 'message' => $message]);
    exit();
}

$file = $_FILES['image'];

// Validate file type
$finfo = new finfo(FILEINFO_MIME_TYPE);
$mimeType = $finfo->file($file['tmp_name']);

if (!in_array($mimeType, $allowedTypes)) {
    echo json_encode(['success' => false, 'message' => 'Invalid file type. Allowed: JPG, PNG, GIF, WEBP']);
    exit();
}

// Validate file size
if ($file['size'] > $maxFileSize) {
    echo json_encode(['success' => false, 'message' => 'File too large. Maximum size: 5MB']);
    exit();
}

// Generate unique filename
$extension = pathinfo($file['name'], PATHINFO_EXTENSION);
$extension = strtolower($extension);
if (!in_array($extension, ['jpg', 'jpeg', 'png', 'gif', 'webp'])) {
    $extension = 'jpg'; // Default extension based on mime type
}
$filename = time() . '_' . bin2hex(random_bytes(8)) . '.' . $extension;
$filepath = $uploadDir . $filename;

// Move uploaded file
if (move_uploaded_file($file['tmp_name'], $filepath)) {
    $fileUrl = $baseUrl . $folder . '/' . $filename;
    echo json_encode([
        'success' => true,
        'url' => $fileUrl,
        'filename' => $filename
    ]);
} else {
    echo json_encode(['success' => false, 'message' => 'Failed to save file']);
}
?>
