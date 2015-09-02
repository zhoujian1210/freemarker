/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package freemarker.template;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import freemarker.core.Environment;
import freemarker.core.Macro;
import freemarker.template.utility.NullWriter;

/**
 * These are things that users shouldn't do, but we shouldn't break backward compatibility without knowing about it.
 */
public class MistakenlyPublicMacroAPIsTest {

    private final Configuration cfg = new Configuration(Configuration.VERSION_2_3_0);
    
    /**
     * Getting the macros from one template, and adding them to another.
     */
    @Test
    public void testMacroCopyingExploit() throws IOException, TemplateException {
        Template tMacros = new Template(null, "<#macro m1>1</#macro><#macro m2>2</#macro>", cfg);
        Map<String, Macro> macros = tMacros.getMacros();
        
        Template t = new Template(null,
                "<@m1/><@m2/><@m3/>"
                + "<#macro m1>1b</#macro><#macro m3>3b</#macro> "
                + "<@m1/><@m2/><@m3/>", cfg);
        t.addMacro(macros.get("m1"));
        t.addMacro(macros.get("m2"));
        
        assertEquals("123b 1b23b", getTemplateOutput(t));
    }
    
    /**
     * Same as {@link #testMacroCopyingExploit()}, but to make it worse, it adds the macros directly through the macro
     * {@link Map}. 
     */
    @Test
    public void testMacroCopyingExploitWithMapModification() throws IOException, TemplateException {
        Template tMacros = new Template(null, "<#macro m1>1</#macro><#macro m2>2</#macro>", cfg);
        Map<String, Macro> macros = tMacros.getMacros();
        
        Template t = new Template(null,
                "<@m1/><@m2/><@m3/>"
                + "<#macro m1>1b</#macro><#macro m3>3b</#macro> "
                + "<@m1/><@m2/><@m3/>", cfg);
        t.getMacros().put("m1", macros.get("m1"));
        t.getMacros().put("whatever", macros.get("m2"));  // Legacy bug: Map key doesn't mater, only original macro name
        
        assertEquals("123b 1b23b", getTemplateOutput(t));
    }    

    @Test
    public void testMacroCopyingExploitAndNamespaces() throws IOException, TemplateException {
        Template tMacros = new Template(null, "<#assign x = 0><#macro m1>${x}</#macro>", cfg);
        Template t = new Template(null, "<#assign x = 1><@m1/>", cfg);
        t.addMacro((Macro) tMacros.getMacros().get("m1"));
        
        assertEquals("1", getTemplateOutput(t));
    }

    @Test
    public void testMacroCopyingFromFTLVariable() throws IOException, TemplateException {
        Template tMacros = new Template(null, "<#assign x = 0><#macro m1>${x}</#macro>", cfg);
        Environment env = tMacros.createProcessingEnvironment(null, NullWriter.INSTANCE);
        env.process();
        TemplateModel m1 = env.getVariable("m1");
        assertThat(m1, instanceOf(Macro.class));
        
        Template t = new Template(null, "<#assign x = 1><@m1/>", cfg);
        t.addMacro((Macro) m1);
        
        assertEquals("1", getTemplateOutput(t));
    }

    /**
     * Same as {@link #testMacroCopyingFromFTLVariable()}, but to make it worse, it adds the macros directly through the
     * {@link Map}.
     */
    @Test
    public void testMacroCopyingFromFTLVariableWithMapModification() throws IOException, TemplateException {
        Template tMacros = new Template(null, "<#assign x = 0><#macro m1>${x}</#macro>", cfg);
        Environment env = tMacros.createProcessingEnvironment(null, NullWriter.INSTANCE);
        env.process();
        TemplateModel m1 = env.getVariable("m1");
        assertThat(m1, instanceOf(Macro.class));
        
        for (int variation : new int[] { 1, 2 }) {
            Template t = new Template(null, "<#assign x = 1><@m1/>", cfg);
            if (variation == 1) {
                t.getMacros().put("m1", m1);
            } else {
                t.getMacros().put("m1", null); // Just so it appears in the Entry Set.
                for (Map.Entry<String, Macro> ent : (Set<Map.Entry>) t.getMacros().entrySet()) {
                    if (ent.getKey().equals("m1")) {
                        ent.setValue((Macro) m1);
                    }
                }
            }
            assertEquals("1", getTemplateOutput(t));
        }
    }
    
    private String getTemplateOutput(Template t) throws TemplateException, IOException {
        StringWriter sw = new StringWriter();
        t.process(null, sw);
        return sw.toString();
    }

}
