/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/**
 * This module is used to override request scope bean configuration enforced by the common code, to untie indexing from the web environment and allow async task
 * processing in a pulling manner. And configure non web context, which should not process user requests.
 * <p>
 * As a replacement for @RequestScope, ThreadScope is used, implementation based on SimpleThreadScope provided by Spring. Beans configuration provided by the
 * common code is overriden with help of BeanFactoryPostProcessor.
 * </p>
 */
package org.opengroup.osdu.indexer.indexing;
