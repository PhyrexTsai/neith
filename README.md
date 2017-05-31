# neith-in-play

Neith is a service that helps user to upload image to AWS S3.

## Design Choise

- Scala : Core language.
- Play : Web framework for API service.
- Redis : Handle user session.
- Swagger : API document and API interface.
- Specs2 : Testing.
- Scoverage : Testing coverage.
- AWS S3 : Image service.

## Deployment

- SBT native packager : Gnerate Docker container for DC/OS.