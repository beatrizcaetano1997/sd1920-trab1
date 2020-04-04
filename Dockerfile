# base image - an image with openjdk  8
FROM nunopreguica/sd1920tpbase

# working directory inside docker image
WORKDIR /home/sd

# copy the jar created by assembly to the docker image
COPY target/*jar-with-dependencies.jar sd1920.jar

COPY messages.props messages.props

# run messages server
CMD ["java", "-cp", "/home/sd/sd1920.jar", "sd1920.trab1.core.servers.DomainServer"]




