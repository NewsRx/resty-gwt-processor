package com.newsrx.restygwt.processor;

import com.google.auto.service.AutoService;
import com.newsrx.restygwt.annotations.RestyGWT;
import com.squareup.javapoet.CodeBlock;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes({"com.newsrx.restygwt.annotations.RestyGWT"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class RestyGWTProcessor extends AbstractProcessor {

    // rtype, method, margs, rtype, rtype, rest, rest, rtype, method, margs
    private static final String METHOD_TEMPLATE = """
                public Promise<%s> %s(%s) {
                    CallbackPromise<%s> callback = new CallbackPromise<>();
                    REST<%s> withCallback = REST.withCallback(callback);
                    %s rest = withCallback.call(%s.rest);
                    %s ignored = rest.%s(%s);
                    return callback.getPromise();
                }
            """;
    // url, rest
    private static final String SET_URL_TEMPLATE = """
                    %s.rest.setBaseUrl(%s);
            """;
    private Types typeUtils;
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        Map<Element, List<Element>> annotatedElements = new LinkedHashMap<>();
        for (TypeElement annotation : annotations) {
            for (Element e : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (!annotatedElements.containsKey(e)) {
                    annotatedElements.put(e, new ArrayList<>());
                }
                annotatedElements.get(e).add(e);
            }

        }
        for (Element parent : annotatedElements.keySet()) {
            String className = parent.asType().toString();
            try {
                writeImplFile(className, annotatedElements.get(parent));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return true; // false = keep going, true = stop
    }

    private void writeImplFile(final String className, List<Element> elements) throws IOException {
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }
        generateWrapperCode(className, elements, packageName, lastDot);
    }

    private void generateWrapperCode(final String className, List<Element> _elements, String packageName, int lastDot)
    throws IOException {

        Element element = _elements.getFirst();
        RestyGWT annotation = element.getAnnotation(RestyGWT.class);
        String url = annotation.url();

        String newClassName = className + "_REST";
        String simpleClassName = newClassName.substring(lastDot + 1);
        String newRestyName = className + "_DirectRestService";
        String simpleRestyName = newRestyName.substring(lastDot + 1);

        final Filer filer = processingEnv.getFiler();
        JavaFileObject builderFile = filer.createSourceFile(newClassName);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            if (packageName != null) {
                out.printf("""
                        package %s;
                        
                        """, packageName);
            }
            out.printf("""
                            import com.google.gwt.core.client.GWT;
                            import %s;
                            
                            import com.newsrx.restygwt.util.CallbackPromise;
                            import elemental2.promise.Promise;
                            
                            import org.fusesource.restygwt.client.DirectRestService;
                            import org.fusesource.restygwt.client.Dispatcher;
                            import org.fusesource.restygwt.client.REST;
                            import org.fusesource.restygwt.client.Resource;
                            import org.fusesource.restygwt.client.RestServiceProxy;
                            
                            interface %s extends %s, DirectRestService {
                            
                                %s rest = GWT.create(%s.class);
                            
                                default void setBaseUrl(String baseUrl) {
                                    final Resource resource = new Resource(baseUrl);
                                    setResource(resource);
                                }
                            
                                default void setResource(Resource resource) {
                                    final RestServiceProxy restServiceProxy = (RestServiceProxy) %s.rest;
                                    restServiceProxy.setResource(resource);
                                }
                            
                                default void setDispatcher(Dispatcher dispatcher) {
                                    final RestServiceProxy restServiceProxy = (RestServiceProxy) %s.rest;
                                    restServiceProxy.setDispatcher(dispatcher);
                                }
                            }
                            
                            """, className, simpleRestyName, className, simpleRestyName, simpleRestyName, //
                    simpleRestyName, simpleRestyName);

            out.printf("""
                    public class %s {
                    
                        public static final %s INSTANCE = new %s();
                    
                        protected %s() {
                    """, simpleClassName, simpleClassName, simpleClassName, simpleClassName);

            if (url != null && !url.isBlank()) {
                out.print(SET_URL_TEMPLATE.formatted(simpleRestyName, CodeBlock.of("$S", url).toString()));
            }

            out.print("""
                        }
                    
                    """);
            TypeElement objectElement = elementUtils.getTypeElement("java.lang.Object");
            Set<String> objectMethods = new HashSet<>();
            for (Element m : elementUtils.getAllMembers(objectElement)) {
                if (m.getKind() == ElementKind.METHOD) {
                    objectMethods.add(m.getSimpleName().toString());
                }
            }
            // Get all methods from the interface + inherited
            TypeElement typeElement = (TypeElement) _elements.getFirst();
            List<? extends Element> allMembers = elementUtils.getAllMembers(typeElement);

            for (Element member : allMembers) {
                if (member.getKind() != javax.lang.model.element.ElementKind.METHOD) {
                    continue;
                }
                ExecutableElement method = (ExecutableElement) member;
                if (objectMethods.contains(method.getSimpleName().toString())) {
                    continue; // skip Object methods
                }
                ExecutableType resolved =
                        (ExecutableType) typeUtils.asMemberOf((DeclaredType) typeElement.asType(), method);

                String returnType = resolved.getReturnType().toString();
                String methodName = method.getSimpleName().toString();

                // Build parameter list
                List<? extends VariableElement> params = method.getParameters();
                List<String> paramDecls = new ArrayList<>();
                List<String> paramNames = new ArrayList<>();
                for (int i = 0; i < params.size(); i++) {
                    VariableElement p = params.get(i);
                    String pType = resolved.getParameterTypes().get(i).toString();
                    String pName = p.getSimpleName().toString();
                    paramDecls.add(pType + " " + pName);
                    paramNames.add(pName);
                }
                String paramDeclStr = String.join(", ", paramDecls);
                String paramNameStr = String.join(", ", paramNames);
                if (returnType.equals("void")) {
                    returnType = "Void";
                }
                // Fill in METHOD_TEMPLATE
                // rtype, method, margs, rtype, rtype, rest, rest, rtype, method, margs
                String stub = METHOD_TEMPLATE.formatted( //
                        returnType,              // %s return type for Promise
                        methodName,              // %s method name
                        paramDeclStr,            // %s parameter declarations
                        returnType,              // %s callback type
                        returnType,              // %s REST type
                        simpleRestyName,         // %s ignored variable type
                        simpleRestyName,         // %s rest service reference
                        returnType,              // %s callback type
                        methodName,              // %s method call
                        paramNameStr             // %s parameter names
                );

                out.println(stub);
            }

            out.printf("""
                    }
                    """);
        }
    }

}
