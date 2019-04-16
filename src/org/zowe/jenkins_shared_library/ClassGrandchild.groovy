/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library

import java.util.logging.Level

class ClassGrandchild extends ClassChild {
    def steps

    void printAllMethods( obj ){
        if( !obj ){
            steps.echo "Object is null\r\n";
            return;
        }
        if( !obj.metaClass && obj.getClass() ){
            printAllMethods( obj.getClass() );
            return;
        }
        def str = "class ${obj.getClass().name} functions:\r\n";
        obj.metaClass.methods.name.unique().each{
            str += it+"(); ";
        }
        steps.echo  "${str}\r\n";
    }


    ClassGrandchild(steps) {
        super(steps)
        steps.echo "ClassGrandchild construction"
        printAllMethods(super)
        steps.echo "super = ${super}"
        steps.echo "super.getClass = ${super.getClass()}"
    }

    void test() {
        steps.echo "ClassGrandchild.test()"
        printAllMethods(super)
        steps.echo "super = ${super}"
        steps.echo "super.getClass = ${super.getClass()}"
        steps.echo "super.test = ${super.test}"
        steps.echo "super.test.getClass = ${super.test.getClass()}"
        super.test()
        steps.echo "ClassGrandchild.test()"
    }
}
