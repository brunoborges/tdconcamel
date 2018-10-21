# Set initial variables
export IMG_NAME="cameldemoapp:latest"
export ACR=cameldemoacr

# Create Container Registry
az acr create --name $ACR --sku Basic --admin-enabled
export ACR_REPO=`az acr show --name $ACR --query loginServer -o tsv`
export ACR_PASS=`az acr credential show --name cameldemoacr --query "passwords[0].value" -o tsv`

# Build the image remotely on ACR
az acr build -r $ACR -t cameldemoapp:latest .
export ACI_INSTANCE=cameldemoapp

# Run the image
az container create --name $ACR --image $ACR_REPO/$IMG_NAME --cpu 2 --memory 2 --registry-login-server $ACR_REPO --registry-username $ACR --registry-password $ACR_PASS --dns-name-label $ACI_INSTANCE --ports 8080
