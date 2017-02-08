package com.ecobee.plugin.ntc.intentions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.ecobee.plugin.ntc.NameThatColor;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.intentions.AndroidAddStringResourceAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.regex.Pattern;

public class NameThatColorIntentionAction extends AndroidAddStringResourceAction {
    private static final Pattern COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})");
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

    private static String getMatchedColorValue(@NotNull Project project, PsiFile file, @NotNull PsiElement element, ResourceType type) {
        if(file instanceof PsiJavaFile) return null;

        String value = getStringLiteralValue(project, element, file, type);

        if(value == null) {
            if (file instanceof XmlFile && element instanceof XmlText) {
                value = ((XmlText) element).getValue();
            }
        }

        if (value != null && !value.isEmpty() && COLOR_PATTERN.matcher(value).find()) {
            return value;
        }
        return null;
    }

    @Override
    protected ResourceType getType() {
        return ResourceType.COLOR;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        PsiElement element = getPsiElement(file, editor);
        return element != null && getMatchedColorValue(project, file, element, getType()) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement element = getPsiElement(file, editor);
        String value;
        if (element != null && (value = getMatchedColorValue(project, file, element, getType())) != null) {
            String colorHexString = StringUtil.unquoteString(value).toUpperCase();
            String colorName = NameThatColor.getColorName(StringUtil.trimStart(colorHexString, "#"));

            if(getStringLiteralValue(project, element, file, getType()) != null) {
                doInvoke(project, editor, file, colorName, element, getType());
            } else {
                doExpandedInvoke(project, editor, file, colorName, element, getType());
            }
        }

    }

    private static void doExpandedInvoke(Project project, Editor editor, PsiFile file, String resName, PsiElement element, ResourceType type) {
        String value = getMatchedColorValue(project, file, element, type);
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
        } else {
            Module facetModule = facet.getModule();
            final CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(facetModule, type, resName, value, true,
                    null, file.getVirtualFile());
            dialog.setTitle("Extract Resource");
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

        if(file instanceof XmlFile && element instanceof XmlText) {
            ((XmlText)element).setValue(ResourceValue.referenceTo('@', null, type.getName(), resName).toString());
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
