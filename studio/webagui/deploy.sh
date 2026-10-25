./build.sh

rm ../jetty/src/main/resources/agui/webapp/*
cp -R dist/* ../jetty/src/main/resources/agui/webapp

rm ../springboot/src/main/resources/agui/static/*
cp -R dist/* ../springboot/src/main/resources/agui/static

#rm ../quarkus/src/main/resources/webapp/*
#cp dist/* ../quarkus/src/main/resources/webapp
