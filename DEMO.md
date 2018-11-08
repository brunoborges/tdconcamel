# Set initial variables
IMG_NAME="tisapp:latest"
ACR=cameldemoacr
GRP=camelDemoGroup

# Create Container Registry (ONLY ONCE)
az acr create --name $ACR -g $GRP --sku Basic --admin-enabled
ACR_REPO=`az acr show --name $ACR  -g $GRP --query loginServer -o tsv`
ACR_PASS=`az acr credential show  -g $GRP --name cameldemoacr --query "passwords[0].value" -o tsv`

# Build the image remotely on ACR
az acr build -r $ACR -t  -g $GRP $IMG_NAME .
ACI_INSTANCE=tisapp

# Run the image
az container create -g $GRP --name $ACR --image $ACR_REPO/$IMG_NAME \
  --cpu 2 --memory 2 --registry-login-server $ACR_REPO \
  --registry-username $ACR --registry-password $ACR_PASS \
  --dns-name-label $ACI_INSTANCE \
  --ports 8080

# Get logs
az container logs -g $GRP --name $ACR --follow

# Delete instance
az container delete -g $GRP --name $ACR 