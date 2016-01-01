package eu.f3rog.blade.weaving;

import eu.f3rog.blade.core.Weave;
import eu.f3rog.blade.weaving.util.AWeaver;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.build.JavassistBuildException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;

import static eu.f3rog.blade.weaving.util.WeavingUtil.getAnnotations;

public class BladeWeaver extends AWeaver {

    private static class Metadata {

        String into;
        CtClass[] args;

    }

    private static final String HELPER_NAME_FORMAT = "%s.%s_Helper";

    /**
     * Constructor
     */
    public BladeWeaver(boolean debug) {
        super(debug);
    }

    @Override
    public boolean shouldTransform(CtClass candidateClass) throws JavassistBuildException {
        try {
            //log("needTransformation ? %s", candidateClass.getName());
            return hasHelper(candidateClass);
        } catch (Exception e) {
            log("needTransformation failed on class %s", candidateClass.getName());
            e.printStackTrace();
            throw new JavassistBuildException(e);
        }
    }

    @Override
    public void applyTransformations(CtClass classToTransform) throws JavassistBuildException {
        log("Applying transformation to %s", classToTransform.getName());
        try {
            CtClass helper = getHelper(classToTransform);

            for (CtMethod method : helper.getDeclaredMethods()) {
                log("method named \"%s\"", method.getName());

                Metadata metadata = loadMetadata(classToTransform.getClassPool(), method);
                if (metadata == null) { // nowhere
                    log(" -> nowhere");
                    continue;
                }

                CtClass[] params = method.getParameterTypes();

                if (metadata.into.isEmpty()) { // into constructor
                    log(" -> into constructor");
                    // TODO : weave
                    throw new IllegalStateException("Weaving into constructor is not implemented yet!");
                } else {
                    String body;
                    if (params.length > 1) {
                        // TODO : ARG X - NOT GOOD :)
                        body = String.format("{ %s.%s(this, $1); }", helper.getName(), method.getName());
                    } else {
                        body = String.format("{ %s.%s(this); }", helper.getName(), method.getName());
                    }
                    // weave into method
                    getAfterBurner().beforeOverrideMethod(body, classToTransform, metadata.into, metadata.args);
                    log(" -> %s weaved into %s", body, metadata.into);
                }
            }

            log("Transformation done");
        } catch (Exception e) {
            log("Transformation failed!");
            e.printStackTrace();
            throw new JavassistBuildException(e);
        }
    }

    private CtClass getHelper(CtClass cls) throws NotFoundException {
        return cls.getClassPool()
                .get(String.format(HELPER_NAME_FORMAT, cls.getPackageName(), cls.getSimpleName()));
    }

    private boolean hasHelper(CtClass cls) {
        try {
            return getHelper(cls) != null;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private Metadata loadMetadata(ClassPool classPool, CtMethod method) throws NotFoundException {
        AnnotationsAttribute attr = getAnnotations(method);
        if (attr == null) {
            return null;
        }

        Annotation a = attr.getAnnotation(Weave.class.getName());
        if (a == null) {
            return null;
        }

        Metadata metadata = new Metadata();
        // get INTO
        metadata.into = a.getMemberValue("into").toString().replaceAll("\"", "");
        // get INTO ARGS
        ArrayMemberValue with = (ArrayMemberValue) a.getMemberValue("args");
        MemberValue[] values = with.getValue();
        metadata.args = new CtClass[values.length];
        for (int i = 0; i < values.length; i++) {
            String className = values[i].toString().replaceAll("\"", "");
            metadata.args[i] = classPool.get(className);
        }

        return metadata;
    }

}
