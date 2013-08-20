/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.persistence.query;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.cassandra.Concurrent;


/**
 * @author tnine
 * 
 */
@Concurrent()
public class SingleOrderByComplexUnionConnectionIT extends AbstractIteratingQueryIT
{
    @Test
    public void singleOrderByComplexUnionConnection() throws Exception {
        singleOrderByComplexUnion(new ConnectionHelper("singleOrderByComplexUnionConnection"));
    }
}
