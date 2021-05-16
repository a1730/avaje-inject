package io.avaje.inject.generator;

import jakarta.inject.Named;

import javax.lang.model.element.TypeElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class TypeReader {

  private final boolean forBean;
  private final TypeElement beanType;
  private final Set<String> importTypes;
  private final TypeExtendsReader extendsReader;
  private final TypeInterfaceReader interfaceReader;
  private final TypeAnnotationReader annotationReader;
  private final ProcessingContext context;
  private String typesRegister;

  TypeReader(TypeElement beanType, ProcessingContext context, Set<String> importTypes) {
    this(true, beanType, context, importTypes);
  }

  TypeReader(TypeElement returnElement, ProcessingContext context) {
    this(false, returnElement, context, new LinkedHashSet<>());
  }

  private TypeReader(boolean forBean, TypeElement beanType, ProcessingContext context, Set<String> importTypes) {
    this.context = context;
    this.forBean = forBean;
    this.beanType = beanType;
    this.importTypes = importTypes;
    this.extendsReader = new TypeExtendsReader(beanType, context);
    this.interfaceReader = new TypeInterfaceReader(beanType, context);
    this.annotationReader = new TypeAnnotationReader(beanType, context);
  }

  String getTypesRegister() {
    return typesRegister;
  }

  List<String> getInterfaces() {
    return interfaceReader.getInterfaceTypes();
  }

  boolean isClosable() {
    return interfaceReader.isCloseable();
  }

  boolean isRequestScopeBean() {
    return annotationReader.isRequestScopeBean();
  }

  void process() {
    extendsReader.process();
    interfaceReader.process();
    if (forBean) {
      annotationReader.process();
    }
    initRegistrationTypes();
  }

  String getName() {
    Named named = beanType.getAnnotation(Named.class);
    if (named != null) {
      return named.value().toLowerCase();
    }
    if (annotationReader.hasQualifierName()) {
      return annotationReader.getQualifierName();
    }
    String derivedName = extendsReader.getQualifierName();
    if (derivedName != null) {
      return derivedName;
    }
    derivedName = interfaceReader.getQualifierName();
    if (derivedName != null) {
      return derivedName;
    }
    return derivedName;
  }

  private void initRegistrationTypes() {
    TypeAppender appender = new TypeAppender(importTypes);
    if (isRequestScopeBean()) {
      List<String> interfaceTypes = interfaceReader.getInterfaceTypes();
      interfaceTypes.remove(extendsReader.getBaseType());
      if (interfaceTypes.isEmpty()) {
        this.typesRegister = null;
      } else {
        appender.add(interfaceTypes);
        this.typesRegister = appender.asString();
      }
      return;
    }
    List<String> interfaceTypes = interfaceReader.getInterfaceTypes();
    if (interfaceTypes.isEmpty()) {
      // only register extends type if no interfaces implemented
      appender.add(extendsReader.getExtendsTypes());
    } else {
      String baseType = extendsReader.getBaseType();
      if (!interfaceTypes.contains(baseType)) {
        appender.add(baseType);
      }
      appender.add(interfaceTypes);
    }
    if (forBean) {
      appender.add(annotationReader.getAnnotationTypes());
    }
    this.typesRegister = appender.asString();
  }

  void addImports(Set<String> importTypes) {
    importTypes.addAll(this.importTypes);
  }
}
