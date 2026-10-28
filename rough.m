function T = extractFeatures(I)
    % I = grayscale image, T = table of Feature / Value
    p = double(I(:));                                   % all pixels as one column
    
    %% 1. FIRST-ORDER (from the histogram, ignores position)
    n1 = {'Mean','Median','Std','Var','Min','Max','Range','Skew','Kurt','RMS','Entropy','Energy','CV'};
    v1 = [mean(p) median(p) std(p) var(p) min(p) max(p) max(p)-min(p) ...
          skewness(p) kurtosis(p) rms(p) entropy(I) sum(p.^2) std(p)/(mean(p)+eps)];
    
    %% 2. GLCM (texture: how often gray-level pairs occur side by side)
    g  = graycomatrix(I, 'Offset', [0 1; -1 1; -1 0; -1 -1], 'Symmetric', true);
    s  = graycoprops(g, {'Contrast','Correlation','Energy','Homogeneity'});
    n2 = {'GLCM_Contrast','GLCM_Correlation','GLCM_Energy','GLCM_Homogeneity'};
    v2 = [mean(s.Contrast) mean(s.Correlation) mean(s.Energy) mean(s.Homogeneity)];
    
    %% 3. LBP (texture: local binary patterns around each pixel)
    lbp = extractLBPFeatures(I, 'Upright', false);      % needs Computer Vision Toolbox
    n3  = {'LBP_Mean','LBP_Std','LBP_Energy'};
    v3  = [mean(lbp) std(lbp) sum(lbp.^2)];
    
    %% 4. SHAPE (regions in the binary mask)
    BW = bwareaopen(imbinarize(I), 30);                 % binarize, remove tiny blobs
    n4 = {'Area','Perimeter','Eccentricity','Solidity','Extent', ...
          'MajorAxisLength','MinorAxisLength','EquivDiameter','Orientation'};
    r  = regionprops(BW, n4);
    v4 = zeros(1, numel(n4));
    if ~isempty(r)
        for k = 1:numel(n4)
            v4(k) = mean([r.(n4{k})]);                  % average over all regions
        end
    end
    
    %% COMBINE
    T = table([n1 n2 n3 n4]', [v1 v2 v3 v4]', 'VariableNames', {'Feature','Value'});
end