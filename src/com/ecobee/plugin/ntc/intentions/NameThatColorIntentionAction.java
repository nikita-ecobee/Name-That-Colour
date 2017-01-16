package com.ecobee.plugin.ntc.intentions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.ecobee.plugin.ntc.NameThatColor;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.regex.Pattern;

public class NameThatColorIntentionAction extends PsiElementBaseIntentionAction {
    private static final Pattern COLOR_PATTERN = Pattern.compile("#([0-8a-fA-F]{8}|[0-8a-fA-F]{6}|[0-8a-fA-F]{3})");
    @NotNull
    @Override
    public String getText() {
        return "Name this color";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        final PsiElement parent = element.getParent();
        return parent instanceof XmlAttributeValue && COLOR_PATTERN.matcher(((XmlAttributeValue)parent).getValue()).find();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        final XmlAttributeValue literal = PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class, false);
        if (literal == null) return;

        String colorHexString = StringUtil.unquoteString(literal.getValue()).toUpperCase();
        String colorName = NameThatColor.getColorName(StringUtil.trimStart(colorHexString, "#"));

        doInvoke(project, editor, colorName, colorHexString, element);
    }

    private static void doInvoke(Project project, Editor editor, String resName, String value, PsiElement element) {
        ResourceType type = ResourceType.COLOR;
        PsiFile file = element.getContainingFile();
        assert value != null;

        final AndroidFacet facet = AndroidFacet.getInstance(file);
        assert facet != null;

        final String aPackage = getPackage(facet);
        if (aPackage == null) {
            Messages.showErrorDialog(project, AndroidBundle.message("package.not.found.error"), CommonBundle.getErrorTitle());
            return;
        }

        if (resName != null && ApplicationManager.getApplication().isUnitTestMode()) {
            String fileName = AndroidResourceUtil.getDefaultResourceFileName(type);
            assert fileName != null;
            VirtualFile resourceDir = facet.getPrimaryResourceDir();
            assert resourceDir != null;
            AndroidResourceUtil.createValueResource(project, resourceDir, resName, type, fileName,
                    Collections.singletonList(ResourceFolderType.VALUES.getName()), value);
        }
        else {
            Module facetModule = facet.getModule();
            final CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(facetModule, type, resName, value, true,
                    null, file.getVirtualFile());
            dialog.setTitle("Extract Color");
            if (!dialog.showAndGet()) {
                return;
            }

            VirtualFile resourceDir = dialog.getResourceDirectory();
            if (resourceDir == null) {
                AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", facetModule));
                return;
            }

            resName = dialog.getResourceName();
            if (!AndroidResourceUtil
                    .createValueResource(project, resourceDir, resName, type, dialog.getFileName(), dialog.getDirNames(), value)) {
                return;
            }
        }

        final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
        if (attribute != null) {
            attribute.setValue(ResourceValue.referenceTo('@', null, type.getName(), resName).toString());
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        UndoUtil.markPsiFileForUndo(file);
    }

    private static String getPackage(@NotNull AndroidFacet facet) {
        Manifest manifest = facet.getManifest();
        if (manifest == null) return null;
        return manifest.getPackage().getValue();
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
