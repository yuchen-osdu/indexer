# Changelog

## [1.1.1](https://github.com/yuchen-osdu/indexer/compare/v1.1.0...v1.1.1) (2026-07-22)


### 🐛 Bug Fixes

* Aws build command ([48de2b9](https://github.com/yuchen-osdu/indexer/commit/48de2b9ead9bd0f80a0bdd1d76a98400ec7672c5))
* **azure:** Netty-bom before core-lib-azure (lettuce 7.5.2 NoClassDefFoundError) ([b99fc2b](https://github.com/yuchen-osdu/indexer/commit/b99fc2b9d8f5132d91c83fe10b6b18729f32f192))
* **azure:** Netty-bom before core-lib-azure (lettuce 7.5.2 NoClassDefFoundError) ([36ffdc5](https://github.com/yuchen-osdu/indexer/commit/36ffdc50ad0a0050209331e2adc9585814edd9ee))
* **azure:** Upgrade core-lib-azure to 3.0.1 ([#12](https://github.com/yuchen-osdu/indexer/issues/12)) ([615b563](https://github.com/yuchen-osdu/indexer/commit/615b563bac04108bbc50ff2c29479cf2ff50377b))
* Catch SchemaProcessingException directly to prevent poison message loop ([436ef2c](https://github.com/yuchen-osdu/indexer/commit/436ef2c39b241b7180c7957173371cbe8e613acc))
* Catch SchemaProcessingException directly to prevent poison message loop ([271e0ae](https://github.com/yuchen-osdu/indexer/commit/271e0ae7f5e2c843f4e7f3020c7d4849a6a5ac87))
* Cve and spring boot version bump ([0e5edca](https://github.com/yuchen-osdu/indexer/commit/0e5edcaec48feec5c47485eceb5baa5f02ddabdd))
* Cve and spring boot version bump ([ee20e10](https://github.com/yuchen-osdu/indexer/commit/ee20e102ac9464d67ef97f670436f11c1cfa4543))
* Disable bagOfWords to complement mapBooleanToString for Azure in M25 ([03a02d4](https://github.com/yuchen-osdu/indexer/commit/03a02d48b91eb95706b65b598759ae030bced790))
* Disable bagOfWords to complement mapBooleanToString for Azure in M25 ([aed6f55](https://github.com/yuchen-osdu/indexer/commit/aed6f55ce54abf3ca5d4890ba89573cfa0417b94))
* Enabling mapping boolean to string fix for AWS ([0826dce](https://github.com/yuchen-osdu/indexer/commit/0826dce9f5cd7f487becbc2c12ef8dd1c045145a))
* Enabling mapping boolean to string fix for AWS ([0c37fc9](https://github.com/yuchen-osdu/indexer/commit/0c37fc9b5fb01fb808d2fc5cc17b0683c161f06e))
* Geospatial validation bug ([e7bd69d](https://github.com/yuchen-osdu/indexer/commit/e7bd69d4428f5042859b7e795bffe7ceae61d79d))
* Geospatial validation bug ([e110ce6](https://github.com/yuchen-osdu/indexer/commit/e110ce6b137e12b5b66aa4b7ffe53a2091d9f039))
* Poll query result count in indexer step methods to eliminate single-shot flakiness ([e17032f](https://github.com/yuchen-osdu/indexer/commit/e17032f3810cbb1909464920114f5ba6acc20bc3))
* Poll query result count in indexer step methods to eliminate single-shot flakiness ([12b6b69](https://github.com/yuchen-osdu/indexer/commit/12b6b69ea1e632c8b13350ec6a6a883e456eb4dd))
* Removed unused comments per sonarqube ([c101014](https://github.com/yuchen-osdu/indexer/commit/c10101407863772db56a90f58aece0f4d0553cc9))
* Removed unused comments per sonarqube ([f3af12f](https://github.com/yuchen-osdu/indexer/commit/f3af12fc2643b99ab2a3542a21148623c88da7cc))
* Removing core profile from aws build command ([c9cd398](https://github.com/yuchen-osdu/indexer/commit/c9cd39897ec46bbe4a07057844458d1f4894b24c))
* Spring cves ([e711e2d](https://github.com/yuchen-osdu/indexer/commit/e711e2dd340df34a0c78de51e03a2c0c088a11e8))
* Spring cves ([62e5ef4](https://github.com/yuchen-osdu/indexer/commit/62e5ef46bb225b613ad2850772f19e7cfff583e0))
* **test:** Add junit-vintage-engine for JUnit 4 test discovery ([a8d26b0](https://github.com/yuchen-osdu/indexer/commit/a8d26b021dc5e57f0a4047dbe2ba95409da1668f))
* **test:** Add junit-vintage-engine for JUnit 4 test discovery ([f27b0c9](https://github.com/yuchen-osdu/indexer/commit/f27b0c90ff7799b7df27e09060d7b6143633e99f))
* Thread Exhaustion and ES Connection Errors by using request-scoped ElasticSearch client ([2864971](https://github.com/yuchen-osdu/indexer/commit/28649712497257875a3683e00449fbd823e71da3))
* Tomcat-core CVE ([682533a](https://github.com/yuchen-osdu/indexer/commit/682533a1c4825645ddc4a9fcfed893b66ae134e3))
* Tomcat-core CVE ([1dfd49b](https://github.com/yuchen-osdu/indexer/commit/1dfd49bd33d003024450cedbbc133e4a0ed8d2b6))
* Tomcat-core security-crypto json-smart netty-common ([220cb5f](https://github.com/yuchen-osdu/indexer/commit/220cb5f5787c751e7e5283376a2f109af4add0c1))
* Tomcat-core security-crypto json-smart netty-common ([9698e2b](https://github.com/yuchen-osdu/indexer/commit/9698e2bdd2a49eecb55abb11764c10c66bb020ca))
* Unique legal-tag names in parallel indexer tests ([bb34a83](https://github.com/yuchen-osdu/indexer/commit/bb34a8308f10c3759a166cf56bbb579a0e4c6756))
* Unique legal-tag names in parallel indexer tests ([1cae375](https://github.com/yuchen-osdu/indexer/commit/1cae3754ff1486098eca06006160b68d02102fc0))


### 🔧 Miscellaneous

* **ci:** Remove IBM jobs from pipeline ([c85543d](https://github.com/yuchen-osdu/indexer/commit/c85543d43139b359f4c0d545d8b2798b5dcb29b4))
* **ci:** Remove IBM jobs from pipeline ([e47892e](https://github.com/yuchen-osdu/indexer/commit/e47892eefdf3155279d71dd6afd1d6979af6606a))
* Complete repository initialization ([1faaa6d](https://github.com/yuchen-osdu/indexer/commit/1faaa6d60e9721a697974bd88713e611b77fc97b))
* Copy configuration and workflows from main branch ([eafa04b](https://github.com/yuchen-osdu/indexer/commit/eafa04bac1eb7f331dad4df696c5f65484eb99fa))
* Defaulting collaboration ff to true to avoid compatibility issues ([e4703dc](https://github.com/yuchen-osdu/indexer/commit/e4703dc24c4d00eb31b4b45f8581b17c10b02c1b))
* Defaulting collaboration ff to true to avoid compatibility issues ([9ce4828](https://github.com/yuchen-osdu/indexer/commit/9ce48282321561fdd30a5a462e79e5cb21796d4b))
* Deleting aws helm chart ([359b741](https://github.com/yuchen-osdu/indexer/commit/359b741c71e88c541d8db53198ba1290056f2a65))
* Deleting aws helm chart ([f96a0e1](https://github.com/yuchen-osdu/indexer/commit/f96a0e1b62df629dc1ad4949c9780c79d84a863b))
* Fixing sonar issues ([3f6e169](https://github.com/yuchen-osdu/indexer/commit/3f6e16981bd7dc94135709848444c4ad0ea044ff))
* Fixing sonar issues ([03202b2](https://github.com/yuchen-osdu/indexer/commit/03202b21540d7289d0e781ee234b42b07a4e65d6))
* Removing helm copy from aws buildspec ([c012554](https://github.com/yuchen-osdu/indexer/commit/c012554dcfb522ec2640d4f0cb89c8e27dbafb45))


### ♻️ Code Refactoring

* **audit:** Encapsulate audit roles and request context in logging layer ([4a2bd12](https://github.com/yuchen-osdu/indexer/commit/4a2bd12e73f914460c93aade608287d16e42e2ce))
* **audit:** Encapsulate audit roles and request context in logging layer ([0354264](https://github.com/yuchen-osdu/indexer/commit/0354264e0e57237d9673aedf6854fd782d05a8a9))
