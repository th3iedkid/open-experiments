This is the Sling based K2 that uses OSGi.

To run
mvn clean install
. tools/version
java  -jar app/target/org.sakaiproject.nakamura.app-${K2VERSION}.jar

This will start Felix configured with Sling and the Sakai K2 bundles.

You will find the bundles under budles and some libraries under libraries.


