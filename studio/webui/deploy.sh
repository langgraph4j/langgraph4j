bun build.sh

rm ../jetty/src/main/resources/webapp/*
cp dist/* ../jetty/src/main/resources/webapp

rm ../springboot/src/main/resources/static/*
cp dist/* ../springboot/src/main/resources/static

rm ../quarkus/src/main/resources/webapp/*
cp dist/* ../quarkus/src/main/resources/webapp
