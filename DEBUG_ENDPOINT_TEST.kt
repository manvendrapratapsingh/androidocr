// DEBUG: Test different Vertex AI endpoint formats
// PROJECT_ID: ambient-stack-467317-n7
// REGION: asia-south1

// Current endpoint (getting 404):
// https://asia-south1-aiplatform.googleapis.com/v1/projects/ambient-stack-467317-n7/locations/asia-south1/publishers/google/models/gemini-1.5-flash-001:generateContent

// Alternative endpoints to try:

// 1. Try without the version suffix:
// https://asia-south1-aiplatform.googleapis.com/v1/projects/ambient-stack-467317-n7/locations/asia-south1/publishers/google/models/gemini-1.5-flash:generateContent

// 2. Try with 'text' endpoint:
// https://asia-south1-aiplatform.googleapis.com/v1/projects/ambient-stack-467317-n7/locations/asia-south1/publishers/google/models/text-bison:generateContent

// 3. Try Gemini Pro:
// https://asia-south1-aiplatform.googleapis.com/v1/projects/ambient-stack-467317-n7/locations/asia-south1/publishers/google/models/gemini-pro:generateContent

// 4. Check if project has Vertex AI API enabled
// Go to: https://console.cloud.google.com/apis/api/aiplatform.googleapis.com/overview?project=ambient-stack-467317-n7

// 5. Verify region availability for Gemini
// Some models may not be available in asia-south1 region

// TROUBLESHOOTING STEPS:
// 1. Verify project ID is correct
// 2. Check if Vertex AI API is enabled
// 3. Verify service account has proper permissions
// 4. Try a different region (us-central1)
// 5. Check if the model name is correct