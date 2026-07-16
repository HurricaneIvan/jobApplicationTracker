#!/bin/bash
# Runs inside the LocalStack container once it is ready.
# Creates the S3 bucket used for application-copy documents.
awslocal s3 mb s3://application-copies || true

# CORS so the browser can PUT/GET presigned URLs directly against LocalStack.
awslocal s3api put-bucket-cors --bucket application-copies --cors-configuration '{
  "CORSRules": [
    {
      "AllowedHeaders": ["*"],
      "AllowedMethods": ["GET", "PUT"],
      "AllowedOrigins": ["*"],
      "ExposeHeaders": ["ETag"]
    }
  ]
}'
echo "LocalStack: bucket application-copies ready"
