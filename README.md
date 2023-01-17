# nest-events

Retrieves events from nest devices via Cloud Functions and stores in BigQuery table. Events containing images are fetched from dropbox and stored in GCS. This was written as a side project with questionable choices in language (Scala) and libraries (Finagle RPC), as that's what I was using at the time

A SDM developer account https://developers.google.com/nest/device-access/get-started is required to access nest events
