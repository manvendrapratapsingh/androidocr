# Model Names to Test (change MODEL_ID in VertexAiClient.kt)

1. `gemini-1.5-flash-001` (current)
2. `gemini-1.5-flash`  
3. `gemini-1.5-pro-001`
4. `gemini-1.0-pro-vision-001`
5. `gemini-1.0-pro-vision`

# Regions to Test (change REGION in VertexAiClient.kt)

1. `asia-south1` (current - Mumbai)
2. `us-central1` (Iowa)  
3. `us-east1` (South Carolina)
4. `europe-west1` (Belgium)

# Current Endpoint Being Tested:
https://asia-south1-aiplatform.googleapis.com/v1/projects/ambient-stack-467317-n7/locations/asia-south1/publishers/google/models/gemini-1.5-flash-001:generateContent

# If still 404, the most likely cause is:
- Model name doesn't exist in that region
- Wrong region (doesn't match Python backend)
- Service account needs specific Vertex AI permissions