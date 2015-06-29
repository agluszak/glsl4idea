/*
 *     Copyright 2010 Jean-Paul Balabanian and Yngve Devik Hammersland
 *
 *     This file is part of glsl4idea.
 *
 *     Glsl4idea is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as
 *     published by the Free Software Foundation, either version 3 of
 *     the License, or (at your option) any later version.
 *
 *     Glsl4idea is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with glsl4idea.  If not, see <http://www.gnu.org/licenses/>.
 */

package glslplugin.lang.elements.declarations;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import glslplugin.lang.elements.GLSLElementImpl;
import glslplugin.lang.elements.GLSLIdentifier;
import glslplugin.lang.elements.expressions.GLSLExpression;
import glslplugin.lang.elements.types.GLSLArrayType;
import glslplugin.lang.elements.types.GLSLQualifiedType;
import glslplugin.lang.elements.types.GLSLType;
import glslplugin.lang.elements.types.GLSLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GLSLDeclarator represents a local or global variable declaration.
 * It may contain one or more declarators.
 *
 * @author Yngve Devik Hammersland
 *         Date: Jan 29, 2009
 *         Time: 7:29:46 PM
 */
public class GLSLDeclarator extends GLSLElementImpl implements PsiNameIdentifierOwner {
    public static final GLSLDeclarator[] NO_DECLARATORS = new GLSLDeclarator[0];

    public GLSLDeclarator(@NotNull ASTNode astNode) {
        super(astNode);
    }

    @Nullable
    public GLSLExpression getInitializerExpression() {
        final GLSLInitializerExpression init = findChildByClass(GLSLInitializerExpression.class);
        if (init != null) {
            return init.getInitializerExpression();
        } else {
            return null;
        }
    }

    @Nullable
    public GLSLInitializer getInitializer(){
        return findChildByClass(GLSLInitializer.class);
    }

    @Override
    @Nullable
    public GLSLIdentifier getNameIdentifier() {
        PsiElement idElement = getFirstChild();
        if (idElement instanceof GLSLIdentifier) {
            return (GLSLIdentifier) idElement;
        } else {
            return null; //May trigger on malformed code
        }
    }

    @NotNull
    public String getName() {
        GLSLIdentifier identifier = getNameIdentifier();
        if (identifier != null) {
            return identifier.getName();
        } else {
            return "(anonymous)";
        }
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        GLSLIdentifier identifier = getNameIdentifier();
        if (identifier != null) {
            return identifier.setName(name);
        } else {
            throw new IncorrectOperationException("Declarator with no name!");
        }
    }

    @Nullable
    public GLSLDeclaration getParentDeclaration() {
        return findParentByClass(GLSLDeclarationImpl.class);
    }

    /**
     * Clarifies array dimensions if this defines implicitly sized array by initializer.
     * Does nothing otherwise.
     */
    private GLSLType clarifyArrayType(GLSLType baseType){
        if(!(baseType instanceof GLSLArrayType))return baseType; //No need to clarify non-array types
        GLSLInitializer rawInitializer = getInitializer();
        if(rawInitializer == null || !(rawInitializer instanceof GLSLInitializerList)) {
            return baseType; //Can't clarify with this
        }

        GLSLInitializerList initializerList = (GLSLInitializerList) rawInitializer;
        final GLSLArrayType arrayType = (GLSLArrayType) baseType;
        final int[] arrayDimensions = arrayType.getDimensions();
        for (int i = 0; i < arrayDimensions.length; i++) {
            GLSLInitializer[] initializers = initializerList.getInitializers();
            if(arrayDimensions[i] == GLSLArrayType.UNDEFINED_SIZE_DIMENSION){
                //Can clarify that!
                arrayDimensions[i] = initializers.length;
            }
            if(initializers.length >= 1 && initializers[0] instanceof GLSLInitializerList){
                initializerList = (GLSLInitializerList) initializers[0];
            }else{
                //Can't clarify any more
                break;
            }
        }
        return baseType; //Dimensions are changed in place, so no need to create new instance
    }

    @NotNull
    public GLSLType getType() {
        GLSLDeclaration declaration = getParentDeclaration();
        if(declaration == null)return GLSLTypes.UNKNOWN_TYPE;
        GLSLTypeSpecifier declarationType = declaration.getTypeSpecifierNode();
        if(declarationType == null)return GLSLTypes.UNKNOWN_TYPE;

        GLSLType declaredType = declarationType.getType();
        if(!declaredType.isValidType())return GLSLTypes.UNKNOWN_TYPE;

        GLSLArraySpecifier[] arraySpecifiers = findChildrenByClass(GLSLArraySpecifier.class);
        if(arraySpecifiers.length == 0){
            return clarifyArrayType(declaredType);
        }else{
            //Must append some dimensions to the type
            if(declaredType instanceof GLSLArrayType){
                //Already an array, must append the dimensions
                GLSLArrayType declaredArrayType = (GLSLArrayType) declaredType;
                int[] existingDimensions = declaredArrayType.getDimensions();
                int[] combinedDimensions = new int[existingDimensions.length + arraySpecifiers.length];
                System.arraycopy(existingDimensions, 0, combinedDimensions, 0, existingDimensions.length);
                for (int i = 0; i < arraySpecifiers.length; i++) {
                    combinedDimensions[i + existingDimensions.length] = arraySpecifiers[i].getDimensionSize();
                }
                return clarifyArrayType(new GLSLArrayType(declaredArrayType.getBaseType(), combinedDimensions));
            }else{
                int[] dimensions = new int[arraySpecifiers.length];
                for (int i = 0; i < dimensions.length; i++) {
                    dimensions[i] = arraySpecifiers[i].getDimensionSize();
                }
                return clarifyArrayType(new GLSLArrayType(declaredType, dimensions));
            }
        }
    }

    @NotNull
    public GLSLQualifiedType getQualifiedType() {
        final GLSLType type = getType();
        final GLSLDeclaration declaration = getParentDeclaration();
        if(declaration == null || declaration.getQualifierList() == null)return new GLSLQualifiedType(type);
        return new GLSLQualifiedType(type, declaration.getQualifierList().getQualifiers());
    }

    @Override
    public String toString() {
        return "Declarator: " + getName() + " : " + getType().getTypename();
    }
}
