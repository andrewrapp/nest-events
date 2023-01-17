# make sure the pubsub retry is > than the timeout defined here
gcloud functions deploy nest-pubsub-push \
--entry-point functions.NestEventsPubSubReceiver \
--runtime java11 \
--memory 512MB \
--trigger-http \
--allow-unauthenticated \
--max-instances=5 \
--timeout=20 \
--set-env-vars clientId= \
--set-env-vars clientSecret= \
--set-env-vars refreshToken= \
