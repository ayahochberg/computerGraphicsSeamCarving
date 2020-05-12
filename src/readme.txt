ShowSeams:
First we duplicate the original working image.
Then we call the findSeam() function : each seam that is found is added to the seams matrix - this.seams.

After we have the array of seams - we loop through the seams array x times when x is the number of seams.
In each iteration we paint the seam’s  path in red in the duplicated picture.