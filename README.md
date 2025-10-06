# Params View (Jenkins Plugin)

List job parameters like the **Build With Parameters** page (default values + static/dynamic choices).

## Build
```bash
mvn -U -DskipTests clean package
```
Install `target/params-view.hpi` in Jenkins.

## Usage
- Navigate to `JOB_URL/params-view/` to view.
- REST: `JOB_URL/params-view/api/json`.


### Build with local settings (resolves Jenkins parent POM)
```bash
mvn -U -DskipTests -s settings-jenkins.xml clean package
```
